package ai.architech.backend.core.documentation;

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
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationLine;
import ai.architech.backend.core.documentation.canonical.DocumentationLineRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersion;
import ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOutcome;
import ai.architech.backend.core.documentation.rendering.DocumentationMarkdownRenderer;
import ai.architech.backend.core.documentation.rendering.DocumentationRender;
import ai.architech.backend.core.documentation.triggers.DocumentationTriggerEvent;
import ai.architech.backend.core.documentation.triggers.DocumentationTriggerService;
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
import java.util.ArrayList;
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
 * AIW-205's own end-to-end vertical slice: wires the real, already-built pieces from every prior
 * ticket in the Website Documentation Agent V1 epic (AIW-187 through AIW-204) into one connected
 * flow per named scenario, entering exclusively through {@link DocumentationTriggerService} - the
 * real caller surface, not the individual components a unit/IT already exercises in isolation -
 * using deterministic fixtures throughout (a mocked {@link AiGateway}, matching {@code
 * DocumentationTriggerServiceIT}/{@code DocumentationGenerationOrchestratorIT}/{@code
 * DocumentationCanonicalPackagePersisterIT}/{@code DocumentationMarkdownRendererIT}'s own idiom):
 * no live model call anywhere, matching {@code WebsiteQaEndToEndVerticalSliceIT}'s own precedent
 * for M4. Seeding/candidate-building helpers are copied from those existing IT files rather than
 * shared across packages, matching this epic's own established per-test-file convention.
 *
 * <p>Individual pieces of this pipeline already have thorough coverage of their own (schema/
 * identity/structure in AIW-195's IT, keys/domains/disclosure/lifecycle in AIW-196's, revisioning/
 * idempotency in AIW-200's, rendering/localization in AIW-201's) - this class's own value is
 * proving the *connected* flow through the one real entry point a caller would actually use,
 * including the error paths and the immutable lifecycle chain across two genuinely different
 * candidates on the same Documentation Line.
 */
@SpringBootTest
@Transactional
class DocumentationAgentV1EndToEndIT {

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
	private DocumentationMarkdownRenderer renderer;

	@Autowired
	private DocumentationLineRepository lineRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AiGateway aiGateway;

	@Test
	void technicalHandoverViaTheAutomaticFullReleaseTriggerProducesACanonicalPackageAndRender() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		ObjectNode candidateJson = buildFullValidTechnicalCandidate("Runtime detail claim text.");
		stubGeneration(candidateJson.toString());
		stubEvaluationAllSupported(candidateJson);

		Optional<DocumentationTriggerService.TriggerDispatchResult> result = triggerService.dispatchAutomaticTrigger(
				projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, "en-GB", false);

		assertThat(result).isPresent();
		assertThat(result.get().outcome()).isInstanceOf(DocumentationGenerationOutcome.Success.class);
		DocumentationPackageVersion version = result.get().packageVersion().orElseThrow();
		assertThat(version.getRevision()).isEqualTo(1);

		var content = objectMapper.readTree(version.getContentJson());
		assertThat(content.path("deterministicReportRefs")).hasSize(5);
		assertThat(content.path("profileRef").asString()).isEqualTo(TECHNICAL_HANDOVER_PROFILE_REF);

		DocumentationRender render = renderer.render(version.getId());
		assertThat(render.getContent()).contains("# Technical Handover Guide");
		assertThat(render.getContent()).contains("Runtime detail claim text.");
		assertThat(render.getContent()).contains("Deployment information is not yet recorded for this release.");
		assertThat(render.getContent()).contains("Deterministic reports:");
		assertThat(render.getContent()).contains("ARTIFACT_VERSION_MANIFEST");
	}

	@Test
	void customerHandoverViaOnDemandGenerationProducesACanonicalPackageWithNoReports() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		ObjectNode candidateJson = buildFullValidCustomerCandidate("Customer-facing claim text.");
		stubGeneration(candidateJson.toString());
		stubEvaluationAllSupported(candidateJson);

		DocumentationTriggerService.TriggerDispatchResult result = triggerService.generateOnDemand(
				projectId, candidate.getId(), qaResult.getId(), CUSTOMER_HANDOVER_PROFILE_REF, "en-GB");

		assertThat(result.outcome()).isInstanceOf(DocumentationGenerationOutcome.Success.class);
		DocumentationPackageVersion version = result.packageVersion().orElseThrow();
		var content = objectMapper.readTree(version.getContentJson());
		assertThat(content.path("deterministicReportRefs")).isEmpty();

		DocumentationRender render = renderer.render(version.getId());
		assertThat(render.getContent()).contains("# Customer Website Handover");
		assertThat(render.getContent()).contains("Customer-facing claim text.");
		assertThat(render.getContent()).contains("No known limitations have been disclosed for this release.");
	}

	@Test
	void customerOnDemandGenerationIsBlockedAtPreflightForAHoldGatedCandidateAndPersistsNothing() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");

		DocumentationTriggerService.TriggerDispatchResult result = triggerService.generateOnDemand(
				projectId, candidate.getId(), qaResult.getId(), CUSTOMER_HANDOVER_PROFILE_REF, "en-GB");

		assertThat(result.outcome()).isInstanceOf(DocumentationGenerationOutcome.Blocked.class);
		assertThat(result.packageVersion()).isEmpty();
		verify(aiGateway, never()).invoke(any());
		assertThat(lineRepository.findByProjectId(projectId)).isEmpty();
	}

	@Test
	void aCandidateThatNeverPassesTheDeterministicGateExhaustsTheGenerationRetryBudgetAndPersistsNothing() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		// Missing every required TECHNICAL_HANDOVER section - fails AIW-195's structure gate on
		// every attempt, never reaches the semantic-consistency call at all.
		ObjectNode invalidCandidate = objectMapper.createObjectNode();
		invalidCandidate.put("schemaVersion", "1.0.0");
		ObjectNode document = objectMapper.createObjectNode();
		document.put("documentType", "TECHNICAL_HANDOVER_GUIDE");
		document.set("sections", objectMapper.createArrayNode());
		ArrayNode documents = objectMapper.createArrayNode();
		documents.add(document);
		invalidCandidate.set("documents", documents);
		stubGeneration(invalidCandidate.toString());

		Optional<DocumentationTriggerService.TriggerDispatchResult> result = triggerService.dispatchAutomaticTrigger(
				projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, "en-GB", false);

		assertThat(result).isPresent();
		assertThat(result.get().outcome())
				.isInstanceOfAny(DocumentationGenerationOutcome.GenerationFailed.class, DocumentationGenerationOutcome.ValidationFailed.class);
		assertThat(result.get().packageVersion()).isEmpty();
		verify(aiGateway, times(3)).invoke(argThat(req -> req != null && GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
		verify(aiGateway, never()).invoke(argThat(req -> req != null && EVALUATION_MODEL_PROFILE.equals(req.modelProfile())));
		assertThat(lineRepository.findByProjectId(projectId)).isEmpty();
	}

	@Test
	void dispatchingTheSameAutomaticTriggerTwiceIsIdempotentAndProducesNoDuplicateRevision() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");

		ObjectNode candidateJson = buildFullValidTechnicalCandidate("Idempotency check claim text.");
		stubGeneration(candidateJson.toString());
		stubEvaluationAllSupported(candidateJson);

		DocumentationPackageVersion first = triggerService
				.dispatchAutomaticTrigger(
						projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, "en-GB", false)
				.orElseThrow()
				.packageVersion()
				.orElseThrow();
		DocumentationPackageVersion second = triggerService
				.dispatchAutomaticTrigger(
						projectId, candidate.getId(), qaResult.getId(), DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, "en-GB", false)
				.orElseThrow()
				.packageVersion()
				.orElseThrow();

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getRevision()).isEqualTo(1);
		assertThat(lineRepository.findByProjectId(projectId)).hasSize(1);
	}

	@Test
	void aGenuinelyDifferentCandidateOnTheSameLineSupersedesTheFirstRevision() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");

		WebsiteImplementationCandidate candidate1 = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult1 = seedQaResult(candidate1, "PASS");
		ObjectNode candidateJson1 = buildFullValidTechnicalCandidate("First revision claim text.");
		stubGeneration(candidateJson1.toString());
		stubEvaluationAllSupported(candidateJson1);
		DocumentationPackageVersion version1 = triggerService
				.dispatchAutomaticTrigger(
						projectId, candidate1.getId(), qaResult1.getId(), DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, "en-GB", false)
				.orElseThrow()
				.packageVersion()
				.orElseThrow();
		assertThat(version1.getRevision()).isEqualTo(1);

		// A remediated Candidate - a distinct, new immutable id, never a mutation of the original.
		WebsiteImplementationCandidate candidate2 = seedCandidateWithDesign(projectId, "prop-b");
		QaResult qaResult2 = seedQaResult(candidate2, "PASS");
		ObjectNode candidateJson2 = buildFullValidTechnicalCandidate("Second, genuinely different revision claim text.");
		stubGeneration(candidateJson2.toString());
		stubEvaluationAllSupported(candidateJson2);
		DocumentationPackageVersion version2 = triggerService
				.dispatchAutomaticTrigger(
						projectId, candidate2.getId(), qaResult2.getId(), DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, "en-GB", false)
				.orElseThrow()
				.packageVersion()
				.orElseThrow();

		assertThat(version2.getId()).isNotEqualTo(version1.getId());
		assertThat(version2.getRevision()).isEqualTo(2);

		var content2 = objectMapper.readTree(version2.getContentJson());
		assertThat(content2.path("supersedesPackageVersionRef").asString()).isEqualTo(version1.getId().toString());

		DocumentationLine line = lineRepository.findById(version1.getDocumentationLineId()).orElseThrow();
		assertThat(line.getCurrentRevision()).isEqualTo(2);
		assertThat(line.getCurrentPackageVersionId()).contains(version2.getId());

		// The first, superseded revision is untouched - historical canonical data is immutable.
		var content1 = objectMapper.readTree(version1.getContentJson());
		assertThat(content1.path("revision").asInt()).isEqualTo(1);
	}

	// -- AiGateway stubbing (mirrors DocumentationTriggerServiceIT/DocumentationGenerationOrchestratorIT) --

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
		List<String> claimKeys = new ArrayList<>();
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

	// -- context/candidate seeding (mirrors DocumentationMarkdownRendererIT/DocumentationCanonicalPackagePersisterIT) --

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

	// -- candidate JSON building (mirrors DocumentationMarkdownRendererIT's own helpers) --

	private ObjectNode buildFullValidCustomerCandidate(String claimText) {
		ObjectNode document = objectMapper.createObjectNode();
		document.put("documentType", "CUSTOMER_WEBSITE_HANDOVER");
		ArrayNode sections = objectMapper.createArrayNode();

		sections.add(sectionWithOneClaim("WEBSITE_OVERVIEW", "C_WEBSITE_OVERVIEW", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim("WEBSITE_STRUCTURE", "C_WEBSITE_STRUCTURE", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim("FEATURES", "C_FEATURES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(
				sectionWithOneClaim("CONTENT_AND_LANGUAGES", "C_CONTENT_AND_LANGUAGES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim("INTEGRATIONS", "C_INTEGRATIONS", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(emptySection("KNOWN_LIMITATIONS"));

		ObjectNode qaStatusClaim = claimNode("C_QA_STATUS", "QA_STATUS", List.of(AUTH_FULL_RELEASE_GATE), claimText);
		ObjectNode approvalClaim = claimNode("C_APPROVAL_STATUS", "APPROVAL_STATUS", List.of(AUTH_CTX_APPROVAL_RECORD), claimText);
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

	private ObjectNode buildFullValidTechnicalCandidate(String claimText) {
		ObjectNode document = objectMapper.createObjectNode();
		document.put("documentType", "TECHNICAL_HANDOVER_GUIDE");
		ArrayNode sections = objectMapper.createArrayNode();

		sections.add(sectionWithOneClaim(
				"IMPLEMENTATION_OVERVIEW", "T_IMPLEMENTATION_OVERVIEW", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim(
				"RUNTIME_AND_ARCHITECTURE",
				"T_RUNTIME_AND_ARCHITECTURE",
				"IMPLEMENTATION_DESCRIPTION",
				AUTH_IMPLEMENTATION_SUMMARY,
				claimText));
		sections.add(sectionWithOneClaim("ROUTING", "T_ROUTING", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim(
				"FUNCTIONAL_BEHAVIOR", "T_FUNCTIONAL_BEHAVIOR", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(
				sectionWithOneClaim("INTEGRATIONS", "T_INTEGRATIONS", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim(
				"TECHNICAL_CONSTRAINTS", "T_TECHNICAL_CONSTRAINTS", "TECHNICAL_CONSTRAINT", AUTH_IMPLEMENTATION_SUMMARY, claimText));

		ObjectNode qaStatusClaim = claimNode("T_QA_STATUS", "QA_STATUS", List.of(AUTH_FULL_RELEASE_GATE), claimText);
		sections.add(sectionWithBlocks("QA_AND_OUTSTANDING_ISSUES", narrativeBlock(qaStatusClaim)));

		sections.add(emptySection("DEPLOYMENT_INFORMATION"));
		sections.add(emptySection("MAINTENANCE_NOTES"));
		sections.add(emptySection("REFERENCES"));

		document.set("sections", sections);

		ArrayNode documents = objectMapper.createArrayNode();
		documents.add(document);

		ObjectNode candidate = objectMapper.createObjectNode();
		candidate.put("schemaVersion", "1.0.0");
		candidate.set("documents", documents);
		return candidate;
	}

	private ObjectNode sectionWithOneClaim(String sectionType, String claimKey, String claimType, String authorityKey, String claimText) {
		ObjectNode claim = claimNode(claimKey, claimType, List.of(authorityKey), claimText);
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

	private ObjectNode claimNode(String claimKey, String claimType, List<String> authorityKeys, String claimText) {
		ObjectNode claim = objectMapper.createObjectNode();
		claim.put("claimKey", claimKey);
		claim.put("claimType", claimType);
		claim.put("derivation", "DIRECT");
		claim.put("text", claimText);
		ArrayNode authorityKeysArray = objectMapper.createArrayNode();
		authorityKeys.forEach(authorityKeysArray::add);
		claim.set("authorityKeys", authorityKeysArray);
		return claim;
	}
}
