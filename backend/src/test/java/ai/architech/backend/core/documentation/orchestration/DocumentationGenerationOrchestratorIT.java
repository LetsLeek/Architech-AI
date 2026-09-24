package ai.architech.backend.core.documentation.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiRequest;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileLoader;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.PolicyEvaluation;
import ai.architech.backend.core.qa.PolicyEvaluationRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real-Postgres proof of AIW-199's own orchestration pipeline. No test here activates the opt-in
 * {@code real-ai} Spring profile - {@link AiGateway} is mocked at the Spring-context level (the
 * same bean {@link ai.architech.backend.core.documentation.generation.DocumentationGenerationRunner}
 * and {@link ai.architech.backend.core.validation.DocumentationFactualConsistencyValidator} both
 * ultimately call), stubbed per-call by inspecting {@link AiRequest#modelProfile()} to tell a
 * generation call ({@code documentation-reasoning}) apart from a semantic-evaluation call ({@code
 * documentation-factual-consistency}) - zero network, zero cost, matching {@link
 * ai.architech.backend.core.documentation.generation.DocumentationGenerationRunnerIT}'s own idiom.
 */
@SpringBootTest
@Transactional
class DocumentationGenerationOrchestratorIT {

	private static final String AUTH_IMPLEMENTATION_SUMMARY = "AUTH_IMPLEMENTATION_SUMMARY";
	private static final String AUTH_FULL_RELEASE_GATE = "AUTH_FULL_RELEASE_GATE";
	private static final String AUTH_CTX_APPROVAL_RECORD = "AUTH_CTX_APPROVAL_RECORD";
	private static final String GENERATION_MODEL_PROFILE = "documentation-reasoning";
	private static final String EVALUATION_MODEL_PROFILE = "documentation-factual-consistency";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private PolicyEvaluationRepository policyEvaluationRepository;

	@Autowired
	private DocumentationProfileLoader profileLoader;

	@Autowired
	private DocumentationGenerationOrchestrator orchestrator;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AiGateway aiGateway;

	@Test
	void happyPathSucceedsOnFirstGenerationAttemptAndFirstEvaluationAttempt() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		ObjectNode validCandidate = buildFullValidCandidate();
		stubGeneration(validCandidate.toString());
		stubEvaluationAllSupported(validCandidate);

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.Success.class);
		DocumentationGenerationOutcome.Success success = (DocumentationGenerationOutcome.Success) outcome;
		assertThat(success.generationAttemptCount()).isEqualTo(1);
		assertThat(success.evaluationAttemptCount()).isEqualTo(1);
		assertThat(success.candidateJson()).isEqualTo(validCandidate.toString());
		assertThat(success.reports()).isEmpty(); // CUSTOMER_HANDOVER declares no deterministicReports
	}

	@Test
	void preflightFailureStopsImmediatelyWithoutInvokingTheModel() {
		UUID projectId = seedProject();
		// deliberately no customer-profile artifact seeded - CUSTOMER_HANDOVER requires it
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.Blocked.class);
		verify(aiGateway, never()).invoke(any());
	}

	@Test
	void contextAssemblyBlockStopsImmediatelyWithoutInvokingTheModel() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		CandidateFinding finding = seedFinding(qaResult, candidate);
		seedPolicyEvaluation(qaResult, candidate, finding, "BLOCK");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.Blocked.class);
		verify(aiGateway, never()).invoke(any());
	}

	@Test
	void retriesGenerationAfterAStructureFailureThenSucceeds() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		ObjectNode validCandidate = buildFullValidCandidate();
		String invalidCandidate = "{\"schemaVersion\":\"1.0.0\",\"documents\":[]}"; // fails schema (minItems 1)

		when(aiGateway.invoke(argThat(req -> req != null && GENERATION_MODEL_PROFILE.equals(req.modelProfile()))))
				.thenReturn(mockResponse(invalidCandidate))
				.thenReturn(mockResponse(validCandidate.toString()));
		stubEvaluationAllSupported(validCandidate);

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.Success.class);
		DocumentationGenerationOutcome.Success success = (DocumentationGenerationOutcome.Success) outcome;
		assertThat(success.generationAttemptCount()).isEqualTo(2);
	}

	@Test
	void exhaustsAllGenerationAttemptsWhenTheCandidateNeverPassesStructure() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		stubGeneration("{\"schemaVersion\":\"1.0.0\",\"documents\":[]}");

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.ValidationFailed.class);
		DocumentationGenerationOutcome.ValidationFailed failed = (DocumentationGenerationOutcome.ValidationFailed) outcome;
		assertThat(failed.generationAttemptCount()).isEqualTo(3);
		assertThat(failed.reasons()).isNotEmpty();
		verify(aiGateway, times(3)).invoke(argThat(req -> req != null && GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
		verify(aiGateway, never()).invoke(argThat(req -> req != null && EVALUATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	@Test
	void postGenerationSecretLeakStopsImmediatelyWithoutFurtherAttempts() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		ObjectNode leaking = buildFullValidCandidate();
		ObjectNode leakingClaim = (ObjectNode)
				leaking.path("documents").get(0).path("sections").get(0).path("blocks").get(0).path("claims").get(0);
		leakingClaim.put("text", "AKIAFAKETESTKEY12345 leaked here");
		stubGeneration(leaking.toString());

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.Blocked.class);
		verify(aiGateway, times(1)).invoke(argThat(req -> req != null && GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
		verify(aiGateway, never()).invoke(argThat(req -> req != null && EVALUATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	@Test
	void aRealSemanticFindingTriggersAFreshGenerationAttemptRatherThanASemanticRetry() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		ObjectNode validCandidate = buildFullValidCandidate();
		stubGeneration(validCandidate.toString());

		when(aiGateway.invoke(argThat(req -> req != null && EVALUATION_MODEL_PROFILE.equals(req.modelProfile()))))
				.thenReturn(mockResponse(evaluationResponseWithOneUnsupported(validCandidate)))
				.thenReturn(mockResponse(evaluationResponseAllSupported(validCandidate)));

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.Success.class);
		DocumentationGenerationOutcome.Success success = (DocumentationGenerationOutcome.Success) outcome;
		assertThat(success.generationAttemptCount()).isEqualTo(2);
		verify(aiGateway, times(2)).invoke(argThat(req -> req != null && GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	@Test
	void evaluationFailedWhenTheSemanticEvaluatorFailsTwiceInARow() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		ObjectNode validCandidate = buildFullValidCandidate();
		stubGeneration(validCandidate.toString());
		when(aiGateway.invoke(argThat(req -> req != null && EVALUATION_MODEL_PROFILE.equals(req.modelProfile()))))
				.thenReturn(mockResponse("not valid json {{{"));

		DocumentationGenerationOutcome outcome = orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		assertThat(outcome).isInstanceOf(DocumentationGenerationOutcome.EvaluationFailed.class);
		DocumentationGenerationOutcome.EvaluationFailed failed = (DocumentationGenerationOutcome.EvaluationFailed) outcome;
		assertThat(failed.generationAttemptCount()).isEqualTo(1);
		assertThat(failed.evaluationAttemptCount()).isEqualTo(2);
		verify(aiGateway, times(2)).invoke(argThat(req -> req != null && EVALUATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	// -- AiGateway stubbing --

	private void stubGeneration(String candidateJson) {
		when(aiGateway.invoke(argThat(req -> req != null && GENERATION_MODEL_PROFILE.equals(req.modelProfile()))))
				.thenReturn(mockResponse(candidateJson));
	}

	private void stubEvaluationAllSupported(ObjectNode candidate) {
		when(aiGateway.invoke(argThat(req -> req != null && EVALUATION_MODEL_PROFILE.equals(req.modelProfile()))))
				.thenReturn(mockResponse(evaluationResponseAllSupported(candidate)));
	}

	private AiResponse mockResponse(String content) {
		return new AiResponse("mock", "mock-model", content, "correlation", null, null, null, null);
	}

	private String evaluationResponseAllSupported(ObjectNode candidate) {
		ArrayNode results = objectMapper.createArrayNode();
		for (String claimKey : collectClaimKeys(candidate)) {
			ObjectNode result = objectMapper.createObjectNode();
			result.put("claimKey", claimKey);
			result.put("outcome", "SUPPORTED");
			results.add(result);
		}
		ObjectNode root = objectMapper.createObjectNode();
		root.set("results", results);
		return root.toString();
	}

	private String evaluationResponseWithOneUnsupported(ObjectNode candidate) {
		List<String> claimKeys = collectClaimKeys(candidate);
		ArrayNode results = objectMapper.createArrayNode();
		for (int i = 0; i < claimKeys.size(); i++) {
			ObjectNode result = objectMapper.createObjectNode();
			result.put("claimKey", claimKeys.get(i));
			if (i == 0) {
				result.put("outcome", "UNSUPPORTED");
				result.put("code", "CERTAINTY_UPGRADE");
				result.put("reason", "Overstates certainty beyond what the cited authority supports.");
			} else {
				result.put("outcome", "SUPPORTED");
			}
			results.add(result);
		}
		ObjectNode root = objectMapper.createObjectNode();
		root.set("results", results);
		return root.toString();
	}

	private List<String> collectClaimKeys(ObjectNode candidate) {
		List<String> claimKeys = new java.util.ArrayList<>();
		for (var section : candidate.path("documents").get(0).path("sections")) {
			for (var block : section.path("blocks")) {
				if ("NARRATIVE".equals(block.path("blockType").asString(null))) {
					for (var claim : block.path("claims")) {
						claimKeys.add(claim.path("claimKey").asString());
					}
				} else if ("LIST".equals(block.path("blockType").asString(null))) {
					for (var item : block.path("items")) {
						for (var claim : item.path("claims")) {
							claimKeys.add(claim.path("claimKey").asString());
						}
					}
				}
			}
		}
		return claimKeys;
	}

	// -- context/candidate seeding (mirrors DocumentationCandidateDeterministicValidatorIT) --

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

	private void seedCustomerProfile(UUID projectId, String businessName) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, "customer-profile"));
		AgentExecution execution = seedSucceededExecution(projectId, "customer-profile-agent");

		ObjectNode business = objectMapper.createObjectNode();
		business.put("name", businessName);
		ObjectNode content = objectMapper.createObjectNode();
		content.set("business", business);
		content.set("openingHours", objectMapper.createArrayNode());

		artifactVersionRepository.saveAndFlush(
				new ArtifactVersion(artifact.getId(), 1, execution.getId(), objectMapper.writeValueAsString(content)));
	}

	private WebsiteImplementationCandidate seedCandidateWithDesign(UUID projectId, String proposalLocalRef) {
		Artifact designArtifact = artifactRepository
				.findByProjectIdAndType(projectId, "design-proposal-set")
				.orElseGet(() -> artifactRepository.saveAndFlush(new Artifact(projectId, "design-proposal-set")));
		int nextVersion = artifactVersionRepository.findByArtifactIdOrderByVersionNumberDesc(designArtifact.getId()).size() + 1;
		AgentExecution designExecution = seedSucceededExecution(projectId, "designer-agent");
		ArtifactVersion designVersion = artifactVersionRepository.saveAndFlush(new ArtifactVersion(
				designArtifact.getId(), nextVersion, designExecution.getId(), "{\"proposals\":[{\"localRef\":\"" + proposalLocalRef + "\"}]}"));

		AgentExecution developerExecution = seedSucceededExecution(projectId, "developer-agent");
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				designVersion.getId().toString(),
				proposalLocalRef,
				"runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				"[]",
				"[]"));
	}

	private QaResult seedQaResult(WebsiteImplementationCandidate candidate, String gateOutcome) {
		AgentExecution qaAgentExecution = seedSucceededExecution(candidate.getProjectId(), "website-qa-agent");
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", "preview-42", "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot =
				qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{\"qaExecutionRef\": \"fixture\"}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));

		return qaResultRepository.saveAndFlush(new QaResult(
				UUID.randomUUID(),
				qaExecution.getId(),
				candidate.getId(),
				"website-qa-full-release@1.0.0",
				inputSnapshot.getId(),
				"COMPLETE",
				"[]",
				"[]",
				"[]",
				"[]",
				"[]",
				"[]",
				gateOutcome,
				"[]",
				evidenceManifest.getId(),
				"{}"));
	}

	private CandidateFinding seedFinding(QaResult qaResult, WebsiteImplementationCandidate candidate) {
		return candidateFindingRepository.saveAndFlush(new CandidateFinding(
				qaResult.getId(),
				qaResult.getQaExecutionId(),
				candidate.getId(),
				"CONTENT_PLACEHOLDER_LEAK",
				"CONTENT_QUALITY",
				"MINOR",
				"[]",
				"Placeholder text found in a secondary section.",
				null,
				null,
				"[]",
				"fp-" + UUID.randomUUID(),
				"{}"));
	}

	private void seedPolicyEvaluation(
			QaResult qaResult, WebsiteImplementationCandidate candidate, CandidateFinding finding, String disposition) {
		policyEvaluationRepository.saveAndFlush(new PolicyEvaluation(
				qaResult.getId(),
				qaResult.getQaExecutionId(),
				candidate.getId(),
				qaResult.getQaProfileRef(),
				"CANDIDATE_FINDING",
				finding.getId().toString(),
				"severity-default",
				disposition,
				null));
	}

	// -- candidate JSON building (mirrors DocumentationCandidateDeterministicValidatorIT's own helpers) --

	private ObjectNode buildFullValidCandidate() {
		ObjectNode document = objectMapper.createObjectNode();
		document.put("documentType", "CUSTOMER_WEBSITE_HANDOVER");
		ArrayNode sections = objectMapper.createArrayNode();

		sections.add(sectionWithOneClaim("WEBSITE_OVERVIEW", "C_WEBSITE_OVERVIEW", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("WEBSITE_STRUCTURE", "C_WEBSITE_STRUCTURE", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("FEATURES", "C_FEATURES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("CONTENT_AND_LANGUAGES", "C_CONTENT_AND_LANGUAGES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("INTEGRATIONS", "C_INTEGRATIONS", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(emptySection("KNOWN_LIMITATIONS"));

		ObjectNode qaStatusClaim = claimNode("C_QA_STATUS", "QA_STATUS", List.of(AUTH_FULL_RELEASE_GATE));
		ObjectNode approvalClaim = claimNode("C_APPROVAL_STATUS", "APPROVAL_STATUS", List.of(AUTH_CTX_APPROVAL_RECORD));
		sections.add(sectionWithBlocks("PROJECT_STATUS", narrativeBlock(qaStatusClaim, approvalClaim)));

		sections.add(emptySection("CHANGE_AND_MAINTENANCE"));

		document.set("sections", sections);

		ArrayNode documents = objectMapper.createArrayNode();
		documents.add(document);

		ObjectNode candidate = objectMapper.createObjectNode();
		candidate.put("schemaVersion", "1.0.0");
		candidate.set("documents", documents);
		return candidate;
	}

	private ObjectNode sectionWithOneClaim(String sectionType, String claimKey, String claimType, String authorityKey) {
		ObjectNode claim = claimNode(claimKey, claimType, List.of(authorityKey));
		return sectionWithBlocks(sectionType, narrativeBlock(claim));
	}

	private ObjectNode sectionWithBlocks(String sectionType, ObjectNode... blocks) {
		ObjectNode section = objectMapper.createObjectNode();
		section.put("sectionType", sectionType);
		ArrayNode blocksArray = objectMapper.createArrayNode();
		for (ObjectNode block : blocks) {
			blocksArray.add(block);
		}
		section.set("blocks", blocksArray);
		return section;
	}

	private ObjectNode emptySection(String sectionType) {
		ObjectNode section = objectMapper.createObjectNode();
		section.put("sectionType", sectionType);
		section.set("blocks", objectMapper.createArrayNode());
		return section;
	}

	private ObjectNode narrativeBlock(ObjectNode... claims) {
		ObjectNode block = objectMapper.createObjectNode();
		block.put("blockType", "NARRATIVE");
		ArrayNode claimsArray = objectMapper.createArrayNode();
		for (ObjectNode claim : claims) {
			claimsArray.add(claim);
		}
		block.set("claims", claimsArray);
		return block;
	}

	private ObjectNode claimNode(String claimKey, String claimType, List<String> authorityKeys) {
		ObjectNode claim = objectMapper.createObjectNode();
		claim.put("claimKey", claimKey);
		claim.put("claimType", claimType);
		claim.put("derivation", "DIRECT");
		claim.put("text", "Example claim text.");
		ArrayNode authorityKeysArray = objectMapper.createArrayNode();
		authorityKeys.forEach(authorityKeysArray::add);
		claim.set("authorityKeys", authorityKeysArray);
		return claim;
	}
}
