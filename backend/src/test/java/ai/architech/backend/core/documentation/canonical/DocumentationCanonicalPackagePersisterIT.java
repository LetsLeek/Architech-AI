package ai.architech.backend.core.documentation.canonical;

import static org.assertj.core.api.Assertions.assertThat;

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
import ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOrchestrator;
import ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOutcome;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileLoader;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.validation.DocumentationSchemaRegistry;
import ai.architech.backend.core.validation.SchemaValidationResult;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

/**
 * Real-Postgres proof of AIW-200's own linear revisioning/idempotency, built on top of a real
 * {@link DocumentationGenerationOutcome.Success} produced by actually running {@link
 * DocumentationGenerationOrchestrator} against a mocked {@link AiGateway} (zero network, zero
 * cost - mirrors {@link ai.architech.backend.core.documentation.orchestration.DocumentationGenerationOrchestratorIT}'s
 * own seeding/stubbing idiom exactly, copied here rather than shared across packages).
 */
@SpringBootTest
@Transactional
class DocumentationCanonicalPackagePersisterIT {

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
	private DocumentationProfileLoader profileLoader;

	@Autowired
	private DocumentationGenerationOrchestrator orchestrator;

	@Autowired
	private DocumentationCanonicalPackagePersister persister;

	@Autowired
	private DocumentationLineRepository lineRepository;

	@Autowired
	private DocumentationPackageVersionRepository packageVersionRepository;

	@Autowired
	private DocumentationSchemaRegistry schemaRegistry;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AiGateway aiGateway;

	@Test
	void firstPersistOnANewLineIsRevisionOneWithNoSupersession() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		ObjectNode validCandidate = buildFullValidCandidate("Example claim text.");
		stubGeneration(validCandidate.toString());
		stubEvaluationAllSupported(validCandidate);

		DocumentationGenerationOutcome.Success success =
				(DocumentationGenerationOutcome.Success) orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		DocumentationPackageVersion version = persister.persist(projectId, success, profile, List.of("INITIAL"));

		assertThat(version.getRevision()).isEqualTo(1);
		assertThat(version.getSupersedesPackageVersionId()).isEmpty();

		SchemaValidationResult schemaResult =
				schemaRegistry.validate("urn:aiw:schema:documentation:documentation-package-version:v1", version.getContentJson());
		assertThat(schemaResult.valid()).as(schemaResult.issues().toString()).isTrue();

		var content = objectMapper.readTree(version.getContentJson());
		String validationResultId = content.path("validationResultRef").asString();
		ArtifactVersion validationResultVersion = artifactVersionRepository.findById(UUID.fromString(validationResultId)).orElseThrow();
		SchemaValidationResult validationSchemaResult = schemaRegistry.validate(
				"urn:aiw:schema:documentation:documentation-validation-result:v1", validationResultVersion.getContent());
		assertThat(validationSchemaResult.valid()).as(validationSchemaResult.issues().toString()).isTrue();
		assertThat(validationResultVersion.getContent()).contains("\"overallResult\":\"PASS\"");
	}

	@Test
	void aDifferentCandidateOnTheSameLineCreatesRevisionTwoSupersedingRevisionOne() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		WebsiteImplementationCandidate candidate1 = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult1 = seedQaResult(candidate1, "PASS");
		ObjectNode candidateJson1 = buildFullValidCandidate("First revision claim text.");
		stubGeneration(candidateJson1.toString());
		stubEvaluationAllSupported(candidateJson1);
		DocumentationGenerationOutcome.Success success1 = (DocumentationGenerationOutcome.Success)
				orchestrator.generate(projectId, candidate1.getId(), qaResult1.getId(), profile, "de-AT");
		DocumentationPackageVersion version1 = persister.persist(projectId, success1, profile, List.of("INITIAL"));

		WebsiteImplementationCandidate candidate2 = seedCandidateWithDesign(projectId, "prop-b");
		QaResult qaResult2 = seedQaResult(candidate2, "PASS");
		ObjectNode candidateJson2 = buildFullValidCandidate("Second, genuinely different revision claim text.");
		stubGeneration(candidateJson2.toString());
		stubEvaluationAllSupported(candidateJson2);
		DocumentationGenerationOutcome.Success success2 = (DocumentationGenerationOutcome.Success)
				orchestrator.generate(projectId, candidate2.getId(), qaResult2.getId(), profile, "de-AT");
		DocumentationPackageVersion version2 = persister.persist(projectId, success2, profile, List.of("MANUAL_REGENERATION"));

		assertThat(version2.getRevision()).isEqualTo(2);
		assertThat(version2.getSupersedesPackageVersionId()).contains(version1.getId());

		DocumentationLine line = lineRepository.findById(version1.getDocumentationLineId()).orElseThrow();
		assertThat(line.getCurrentRevision()).isEqualTo(2);
		assertThat(line.getCurrentPackageVersionId()).contains(version2.getId());
	}

	@Test
	void retryingTheExactSameCandidateOnTheSameLineReturnsTheSameVersionWithoutANewRevision() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		ObjectNode validCandidate = buildFullValidCandidate("Idempotent claim text.");
		stubGeneration(validCandidate.toString());
		stubEvaluationAllSupported(validCandidate);

		DocumentationGenerationOutcome.Success success =
				(DocumentationGenerationOutcome.Success) orchestrator.generate(projectId, candidate.getId(), qaResult.getId(), profile, "de-AT");

		DocumentationPackageVersion first = persister.persist(projectId, success, profile, List.of("INITIAL"));
		long countAfterFirst = packageVersionRepository.count();

		DocumentationPackageVersion second = persister.persist(projectId, success, profile, List.of("INITIAL"));

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(packageVersionRepository.count()).isEqualTo(countAfterFirst);
	}

	@Test
	void twoDifferentLocaleLinesForTheSameProjectNeverSupersedeEachOther() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		WebsiteImplementationCandidate candidateDe = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResultDe = seedQaResult(candidateDe, "PASS");
		ObjectNode candidateDeJson = buildFullValidCandidate("German locale claim text.");
		stubGeneration(candidateDeJson.toString());
		stubEvaluationAllSupported(candidateDeJson);
		DocumentationGenerationOutcome.Success successDe = (DocumentationGenerationOutcome.Success)
				orchestrator.generate(projectId, candidateDe.getId(), qaResultDe.getId(), profile, "de-AT");
		DocumentationPackageVersion versionDe = persister.persist(projectId, successDe, profile, List.of("INITIAL"));

		WebsiteImplementationCandidate candidateEn = seedCandidateWithDesign(projectId, "prop-b");
		QaResult qaResultEn = seedQaResult(candidateEn, "PASS");
		ObjectNode candidateEnJson = buildFullValidCandidate("English locale claim text.");
		stubGeneration(candidateEnJson.toString());
		stubEvaluationAllSupported(candidateEnJson);
		DocumentationGenerationOutcome.Success successEn = (DocumentationGenerationOutcome.Success)
				orchestrator.generate(projectId, candidateEn.getId(), qaResultEn.getId(), profile, "en-GB");
		DocumentationPackageVersion versionEn = persister.persist(projectId, successEn, profile, List.of("INITIAL"));

		assertThat(versionDe.getRevision()).isEqualTo(1);
		assertThat(versionEn.getRevision()).isEqualTo(1);
		assertThat(versionDe.getDocumentationLineId()).isNotEqualTo(versionEn.getDocumentationLineId());
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

	private ObjectNode buildFullValidCandidate(String claimText) {
		ObjectNode document = objectMapper.createObjectNode();
		document.put("documentType", "CUSTOMER_WEBSITE_HANDOVER");
		ArrayNode sections = objectMapper.createArrayNode();

		sections.add(sectionWithOneClaim("WEBSITE_OVERVIEW", "C_WEBSITE_OVERVIEW", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim("WEBSITE_STRUCTURE", "C_WEBSITE_STRUCTURE", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim("FEATURES", "C_FEATURES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim(
				"CONTENT_AND_LANGUAGES", "C_CONTENT_AND_LANGUAGES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY, claimText));
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
