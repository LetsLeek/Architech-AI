package ai.architech.backend.core.documentation.triggers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersion;
import ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOutcome;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.List;
import java.util.Optional;
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
 * Real-Postgres proof of AIW-202's own dispatch wiring - the first end-to-end exercise of trigger
 * → {@link ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOrchestrator}
 * → {@link ai.architech.backend.core.documentation.canonical.DocumentationCanonicalPackagePersister}.
 * No test activates the opt-in {@code real-ai} Spring profile - {@link AiGateway} is mocked at the
 * Spring-context level, same idiom as {@code DocumentationGenerationOrchestratorIT}/{@code
 * DocumentationCanonicalPackagePersisterIT} (seeding helpers copied from there rather than shared
 * across packages, matching this codebase's own established per-test-file convention).
 */
@SpringBootTest
@Transactional
class DocumentationTriggerServiceIT {

	private static final String AUTH_IMPLEMENTATION_SUMMARY = "AUTH_IMPLEMENTATION_SUMMARY";
	private static final String AUTH_FULL_RELEASE_GATE = "AUTH_FULL_RELEASE_GATE";
	private static final String AUTH_CTX_APPROVAL_RECORD = "AUTH_CTX_APPROVAL_RECORD";
	private static final String GENERATION_MODEL_PROFILE = "documentation-reasoning";
	private static final String EVALUATION_MODEL_PROFILE = "documentation-factual-consistency";
	private static final String CUSTOMER_HANDOVER_PROFILE_REF = "CUSTOMER_HANDOVER@1.0.0";
	private static final String TECHNICAL_HANDOVER_PROFILE_REF = "TECHNICAL_HANDOVER@1.0.0";

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
	private DocumentationTriggerService triggerService;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AiGateway aiGateway;

	@Test
	void scopedApprovalRecordedWithConfirmationDispatchesAndCanonicalizes() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		ObjectNode validCandidate = buildFullValidCustomerCandidate();
		stubGeneration(validCandidate.toString());
		stubEvaluationAllSupported(validCandidate);

		Optional<DocumentationTriggerService.TriggerDispatchResult> result = triggerService.dispatchAutomaticTrigger(
				projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.SCOPED_APPROVAL_RECORDED, "de-AT", true);

		assertThat(result).isPresent();
		assertThat(result.get().outcome()).isInstanceOf(DocumentationGenerationOutcome.Success.class);
		assertThat(result.get().packageVersion()).isPresent();
		assertThat(result.get().packageVersion().get().getRevision()).isEqualTo(1);
	}

	@Test
	void scopedApprovalRecordedWithoutConfirmationNeverDispatches() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		Optional<DocumentationTriggerService.TriggerDispatchResult> result = triggerService.dispatchAutomaticTrigger(
				projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.SCOPED_APPROVAL_RECORDED, "de-AT", false);

		assertThat(result).isEmpty();
		verify(aiGateway, never()).invoke(any());
	}

	@Test
	void fullReleaseQaFinalizedForAHoldGatedCandidateNeverDispatches() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");

		Optional<DocumentationTriggerService.TriggerDispatchResult> result = triggerService.dispatchAutomaticTrigger(
				projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, "de-AT", false);

		assertThat(result).isEmpty();
		verify(aiGateway, never()).invoke(any());
	}

	@Test
	void dispatchingTheSameTriggerTwiceIsIdempotentAndProducesNoDuplicateRevision() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		ObjectNode validCandidate = buildFullValidCustomerCandidate();
		stubGeneration(validCandidate.toString());
		stubEvaluationAllSupported(validCandidate);

		DocumentationPackageVersion first = triggerService
				.dispatchAutomaticTrigger(
						projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.SCOPED_APPROVAL_RECORDED, "de-AT", true)
				.orElseThrow()
				.packageVersion()
				.orElseThrow();
		DocumentationPackageVersion second = triggerService
				.dispatchAutomaticTrigger(
						projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.SCOPED_APPROVAL_RECORDED, "de-AT", true)
				.orElseThrow()
				.packageVersion()
				.orElseThrow();

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getRevision()).isEqualTo(1);
	}

	@Test
	void onDemandTechnicalHandoverSucceedsAtPreflightForAHoldGatedCandidate() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");

		stubGeneration("{\"schemaVersion\":\"1.0.0\",\"documents\":[]}"); // intentionally invalid - only preflight matters here

		DocumentationTriggerService.TriggerDispatchResult result =
				triggerService.generateOnDemand(projectId, candidate.getId(), qaResult.getId(), TECHNICAL_HANDOVER_PROFILE_REF, "en-GB");

		assertThat(result.outcome()).isNotInstanceOf(DocumentationGenerationOutcome.Blocked.class);
		verify(aiGateway, org.mockito.Mockito.atLeastOnce())
				.invoke(argThat(req -> req != null && GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	@Test
	void onDemandCustomerHandoverIsBlockedAtPreflightForAHoldGatedCandidate() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");

		DocumentationTriggerService.TriggerDispatchResult result =
				triggerService.generateOnDemand(projectId, candidate.getId(), qaResult.getId(), CUSTOMER_HANDOVER_PROFILE_REF, "de-AT");

		assertThat(result.outcome()).isInstanceOf(DocumentationGenerationOutcome.Blocked.class);
		assertThat(result.packageVersion()).isEmpty();
		verify(aiGateway, never()).invoke(any());
	}

	// -- AiGateway stubbing (mirrors DocumentationGenerationOrchestratorIT) --

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

	// -- context/candidate seeding (mirrors DocumentationGenerationOrchestratorIT) --

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

	// -- candidate JSON building (mirrors DocumentationGenerationOrchestratorIT's own helpers) --

	private ObjectNode buildFullValidCustomerCandidate() {
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
