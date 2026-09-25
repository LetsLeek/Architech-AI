package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiMessage;
import ai.architech.backend.core.ai.AiRequest;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import ai.architech.backend.core.verification.VerificationOutcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real, end-to-end integration coverage for AIW-213's own missing glue: proves {@link
 * QaTriggerService} is a genuine first real caller of {@link QaSemanticReviewOrchestrator#run}
 * (via a freshly assembled {@link QaExecutionInputAssembler} payload, preflight-validated for
 * real by {@link ai.architech.backend.core.validation.QAExecutionPreflightValidator}), that a
 * {@code PASS} gate outcome produces both a real, persisted {@code QaResult} and a real {@code
 * FULL_RELEASE_QA_FINALIZED} Documentation dispatch attempt, and that a {@code HOLD} gate outcome
 * produces the {@code QaResult} but never dispatches Documentation at all.
 *
 * <p>No live model call anywhere: {@link AiGateway} is mocked at the Spring-context level, the
 * same idiom {@code DocumentationTriggerServiceIT}/{@code QaResultAssemblyServiceIT} already
 * establish. Because the real {@link QaExecutionInputAssembler} generates fresh {@code
 * qaExecutionRef}/{@code inputSnapshotRef} placeholders on every call (by design - see that
 * class's own javadoc), the QA-agent stub reads the actual assembled {@code qa-execution-input}
 * straight out of the captured {@link AiRequest}'s own prompt (the same embedding {@code
 * AgentRunner#buildMessagesFromInputArtifacts} always produces) and echoes those exact values
 * back, rather than hard-coding them - the same identity-echo {@link
 * ai.architech.backend.core.validation.SemanticQaReviewOutputIdentityValidator} itself checks.
 */
@SpringBootTest
@Transactional
class QaTriggerServiceIT {

	private static final String QA_MODEL_PROFILE = "qa-reasoning";
	private static final String DOCUMENTATION_GENERATION_MODEL_PROFILE = "documentation-reasoning";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private RunnerVerificationRunRepository runnerVerificationRunRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private QaTriggerService qaTriggerService;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AiGateway aiGateway;

	@Test
	void aPassingGateOutcomePersistsARealQaResultAndDispatchesDocumentation() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "customer-profile");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, "prop-a");

		stubQaResponse(this::passingBody);
		stubDocumentationGeneration();

		QaTriggerOutcome outcome = qaTriggerService.triggerFullReleaseQa(projectId, candidate);

		assertThat(outcome.succeeded()).as(outcome.failureMessage()).isTrue();
		assertThat(outcome.qaResult().getGateOutcome()).isEqualTo("PASS");
		assertThat(outcome.qaResult().getTestedCandidateId()).isEqualTo(candidate.getId());
		assertThat(qaResultRepository.findById(outcome.qaResult().getId())).isPresent();
		assertThat(outcome.documentationDispatch()).isPresent();

		verify(aiGateway, atLeastOnce()).invoke(argThat(req -> req != null && QA_MODEL_PROFILE.equals(req.modelProfile())));
		verify(aiGateway, atLeastOnce())
				.invoke(argThat(req -> req != null && DOCUMENTATION_GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	@Test
	void aHoldGateOutcomePersistsARealQaResultButNeverDispatchesDocumentation() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "customer-profile");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, "prop-a");

		stubQaResponse(this::holdBody);

		QaTriggerOutcome outcome = qaTriggerService.triggerFullReleaseQa(projectId, candidate);

		assertThat(outcome.succeeded()).as(outcome.failureMessage()).isTrue();
		assertThat(outcome.qaResult().getGateOutcome()).isEqualTo("HOLD");
		assertThat(qaResultRepository.findById(outcome.qaResult().getId())).isPresent();
		assertThat(outcome.documentationDispatch()).isEmpty();

		verify(aiGateway, never())
				.invoke(argThat(req -> req != null && DOCUMENTATION_GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	@Test
	void anInfrastructureFailureBeforeTheModelIsEverCalledIsReportedRatherThanThrown() {
		UUID projectId = seedProject();
		// Deliberately no canonical customer-profile/website-requirements artifacts seeded - the
		// assembler itself must fail with ApplicationException(CANONICAL_ARTIFACT_NOT_FOUND).
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, "prop-a");

		QaTriggerOutcome outcome = qaTriggerService.triggerFullReleaseQa(projectId, candidate);

		assertThat(outcome.succeeded()).isFalse();
		assertThat(outcome.failureMessage()).isNotBlank();
		assertThat(outcome.documentationDispatch()).isEmpty();
		verify(aiGateway, never()).invoke(org.mockito.ArgumentMatchers.any());
	}

	// -- AiGateway stubbing --

	private void stubQaResponse(java.util.function.Function<JsonNode, String> bodyBuilder) {
		when(aiGateway.invoke(argThat(req -> req != null && QA_MODEL_PROFILE.equals(req.modelProfile()))))
				.thenAnswer(invocation -> {
					AiRequest request = invocation.getArgument(0);
					JsonNode input = extractQaExecutionInput(request);
					String body = bodyBuilder.apply(input);
					return mockResponse("{\"semantic-qa-review-output\": " + body + "}");
				});
	}

	private void stubDocumentationGeneration() {
		// Content is deliberately not a valid documentation-package-candidate - this test only
		// needs to prove the FULL_RELEASE_QA_FINALIZED trigger actually dispatched a generation
		// attempt, not that end-to-end Documentation generation itself succeeds (separate, already
		// covered scope - see DocumentationTriggerServiceIT/DocumentationGenerationOrchestratorIT).
		when(aiGateway.invoke(argThat(req -> req != null && DOCUMENTATION_GENERATION_MODEL_PROFILE.equals(req.modelProfile()))))
				.thenReturn(mockResponse("{\"schemaVersion\":\"1.0.0\",\"documents\":[]}"));
	}

	private AiResponse mockResponse(String content) {
		return new AiResponse("mock", "mock-model", content, "correlation", null, null, null, null);
	}

	private JsonNode extractQaExecutionInput(AiRequest request) {
		String marker = "[qa-execution-input]\n";
		String userContent = request.messages().stream()
				.filter(m -> "user".equals(m.role()))
				.map(AiMessage::content)
				.findFirst()
				.orElseThrow();
		int idx = userContent.indexOf(marker);
		String json = userContent.substring(idx + marker.length()).trim();
		return objectMapper.readTree(json);
	}

	private String passingBody(JsonNode input) {
		ObjectNode body = envelopeFields(input);
		body.set("findingCandidates", objectMapper.createArrayNode());
		body.set("authorityIssueCandidates", objectMapper.createArrayNode());
		body.set("evaluationIssueCandidates", objectMapper.createArrayNode());
		body.set("semanticReviewCoverage", coverage());
		return body.toString();
	}

	private String holdBody(JsonNode input) {
		ObjectNode body = envelopeFields(input);
		ArrayNode findings = objectMapper.createArrayNode();
		ObjectNode finding = objectMapper.createObjectNode();
		finding.put("localRef", "finding-1");
		finding.put("findingCode", "BROKEN_PRIMARY_NAV_LINK");
		finding.put("primaryDomain", "NAVIGATION");
		finding.put("proposedSeverity", "CRITICAL");
		ArrayNode normativeBasis = objectMapper.createArrayNode();
		ObjectNode basis = objectMapper.createObjectNode();
		basis.put("type", "SOURCE_DESIGN");
		basis.put("ref", input.path("productAuthority").path("sourceDesignRef").asString());
		normativeBasis.add(basis);
		finding.set("normativeBasis", normativeBasis);
		finding.put("summary", "The primary navigation link to the pricing page is broken.");
		ArrayNode evidenceRefs = objectMapper.createArrayNode();
		evidenceRefs.add("ev-1");
		finding.set("evidenceRefs", evidenceRefs);
		ObjectNode context = objectMapper.createObjectNode();
		context.put("route", "/pricing");
		context.put("viewportRef", "FULL_WIDE");
		finding.set("context", context);
		findings.add(finding);

		body.set("findingCandidates", findings);
		body.set("authorityIssueCandidates", objectMapper.createArrayNode());
		body.set("evaluationIssueCandidates", objectMapper.createArrayNode());
		body.set("semanticReviewCoverage", coverage());
		return body.toString();
	}

	private ObjectNode envelopeFields(JsonNode input) {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("schemaVersion", "1.0.0");
		body.put("qaExecutionRef", input.path("qaExecutionRef").asString());
		body.put("testedCandidateRef", input.path("target").path("candidateRef").asString());
		body.put("qaProfileRef", input.path("qaAuthority").path("qaProfileRef").asString());
		body.put("inputSnapshotRef", input.path("provenance").path("inputSnapshotRef").asString());
		return body;
	}

	private ArrayNode coverage() {
		ArrayNode coverage = objectMapper.createArrayNode();
		ObjectNode entry = objectMapper.createObjectNode();
		entry.put("reviewTaskRef", "review-navigation-1");
		entry.put("domain", "NAVIGATION");
		entry.put("status", "COMPLETED");
		ArrayNode evidenceRefs = objectMapper.createArrayNode();
		evidenceRefs.add("ev-1");
		entry.set("evidenceRefs", evidenceRefs);
		coverage.add(entry);
		return coverage;
	}

	// -- seeding --

	private UUID seedProject() {
		return projectRepository.saveAndFlush(new Project("website")).getId();
	}

	private AgentExecution seedSucceededExecution(UUID projectId, String agentId) {
		AgentExecution execution = new AgentExecution(projectId, agentId, 1);
		execution.start();
		execution.succeed();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private void seedCanonicalArtifact(UUID projectId, String type) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = seedSucceededExecution(projectId, type + "-agent");
		artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), "{\"content\":\"fixture\"}"));
	}

	private WebsiteImplementationCandidate seedCandidate(UUID projectId, String proposalLocalRef) {
		Artifact designArtifact = artifactRepository.saveAndFlush(new Artifact(projectId, "design-proposal-set"));
		AgentExecution designExecution = seedSucceededExecution(projectId, "designer-agent");
		ArtifactVersion designVersion = artifactVersionRepository.saveAndFlush(new ArtifactVersion(
				designArtifact.getId(), 1, designExecution.getId(), "{\"proposals\":[{\"localRef\":\"" + proposalLocalRef + "\"}]}"));

		AgentExecution developerExecution = seedSucceededExecution(projectId, "developer-agent");
		String repositoryStateRef = "snapshot-hash-" + UUID.randomUUID();
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				designVersion.getId().toString(),
				proposalLocalRef,
				"runtime-v1",
				repositoryStateRef,
				"summary",
				"[]",
				"[]",
				"[]"));

		runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(developerExecution.getId(), repositoryStateRef, VerificationOutcome.PASS));

		return candidate;
	}
}
