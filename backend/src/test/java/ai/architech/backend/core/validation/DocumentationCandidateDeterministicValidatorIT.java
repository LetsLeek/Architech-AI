package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.context.DocumentationContextAssembler;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proves AIW-196's five DETERMINISTIC.md areas (minus what AIW-195 already covers, see {@link
 * DocumentationCandidateStructureValidator}'s javadoc for the exact boundary) against a real,
 * assembled {@link DocumentationContext} - so the authority/disclosure keys under test are
 * exactly what real production code produces, not invented ones that happen to match.
 */
@SpringBootTest
@Transactional
class DocumentationCandidateDeterministicValidatorIT {

	private static final String AUTH_IMPLEMENTATION_SUMMARY = "AUTH_IMPLEMENTATION_SUMMARY";
	private static final String AUTH_FULL_RELEASE_GATE = "AUTH_FULL_RELEASE_GATE";
	private static final String AUTH_CTX_APPROVAL_RECORD = "AUTH_CTX_APPROVAL_RECORD";

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
	private DocumentationContextAssembler contextAssembler;

	@Autowired
	private DocumentationCandidateDeterministicValidator validator;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void acceptsAFullyValidZeroFindingCandidate() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		String candidateJson = buildFullValidCandidate(false, null, null).toString();

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, context, profile);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void acceptsAFullyValidCandidateWithOneDisclosedFinding() {
		DocumentationContext context = seedContext(true);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		JsonNode disclosureEntry = firstDisclosureEntry(context);
		String candidateJson = buildFullValidCandidate(
						true, disclosureEntry.path("disclosureKey").asString(), disclosureEntry.path("findingAuthorityKey").asString())
				.toString();

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, context, profile);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void rejectsSectionOrderViolation() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		ObjectNode candidate = buildFullValidCandidate(false, null, null);
		swapSections(candidate, "WEBSITE_STRUCTURE", "FEATURES");

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("structure") && issue.reason().contains("section order"));
	}

	@Test
	void rejectsDuplicateClaimKeyAcrossDifferentSections() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		ObjectNode candidate = buildFullValidCandidate(false, null, null);
		JsonNode websiteOverviewClaim = findFirstClaimInSection(candidate, "WEBSITE_OVERVIEW");
		JsonNode websiteStructureClaim = findFirstClaimInSection(candidate, "WEBSITE_STRUCTURE");
		((ObjectNode) websiteStructureClaim).put("claimKey", websiteOverviewClaim.path("claimKey").asString());

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("structure") && issue.reason().contains("duplicate claimKey"));
	}

	@Test
	void rejectsAnUnresolvableAuthorityKey() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		ObjectNode candidate = buildFullValidCandidate(false, null, null);
		JsonNode claim = findFirstClaimInSection(candidate, "WEBSITE_OVERVIEW");
		((ArrayNode) claim.path("authorityKeys")).set(0, "AUTH_DOES_NOT_EXIST");

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("keys") && issue.reason().contains("AUTH_DOES_NOT_EXIST"));
	}

	@Test
	void rejectsAnUnresolvableDisclosureKey() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		ObjectNode candidate = buildFullValidCandidate(false, null, null);
		ObjectNode claim = (ObjectNode) findFirstClaimInSection(candidate, "WEBSITE_OVERVIEW");
		ArrayNode disclosureKeys = objectMapper.createArrayNode();
		disclosureKeys.add("DISC_DOES_NOT_EXIST");
		claim.set("disclosureKeys", disclosureKeys);

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("keys") && issue.reason().contains("DISC_DOES_NOT_EXIST"));
	}

	@Test
	void rejectsAQaStatusClaimCitingOnlyAnUnrelatedDomainAuthorityKey() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		ObjectNode candidate = buildFullValidCandidate(false, null, null);
		JsonNode qaStatusClaim = findFirstClaimOfType(candidate, "QA_STATUS");
		((ArrayNode) qaStatusClaim.path("authorityKeys")).set(0, AUTH_IMPLEMENTATION_SUMMARY);

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("domains") && issue.reason().contains("QA_STATUS"));
	}

	@Test
	void rejectsAQaStatusClaimMissingTheGateAuthorityKey() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		ObjectNode candidate = buildFullValidCandidate(false, null, null);
		JsonNode qaStatusClaim = findFirstClaimOfType(candidate, "QA_STATUS");
		// Still a QA_EVALUATION-domain key so the domains check passes, but not the exact gate key.
		((ArrayNode) qaStatusClaim.path("authorityKeys")).removeAll();
		((ArrayNode) qaStatusClaim.path("authorityKeys")).add(AUTH_IMPLEMENTATION_SUMMARY);

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("lifecycle") && issue.reason().contains("QA_STATUS"));
	}

	@Test
	void rejectsAnApprovalStatusClaimMissingTheContextStateAuthorityKey() {
		DocumentationContext context = seedContext(false);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		ObjectNode candidate = buildFullValidCandidate(false, null, null);
		JsonNode approvalClaim = findFirstClaimOfType(candidate, "APPROVAL_STATUS");
		((ArrayNode) approvalClaim.path("authorityKeys")).set(0, AUTH_IMPLEMENTATION_SUMMARY);

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("lifecycle") && issue.reason().contains("APPROVAL_STATUS"));
	}

	@Test
	void rejectsAMissingDisclosureCoverageForADisclosedFinding() {
		DocumentationContext context = seedContext(true);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		// Zero-finding-shaped candidate: no claim anywhere cites the real finding's keys.
		String candidateJson = buildFullValidCandidate(false, null, null).toString();

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("disclosure"));
	}

	@Test
	void rejectsADisclosureClaimPlacedInTheWrongSection() {
		DocumentationContext context = seedContext(true);
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		JsonNode disclosureEntry = firstDisclosureEntry(context);
		ObjectNode candidate = buildFullValidCandidate(
				true, disclosureEntry.path("disclosureKey").asString(), disclosureEntry.path("findingAuthorityKey").asString());
		// Move the disclosure claim out of KNOWN_LIMITATIONS into WEBSITE_OVERVIEW.
		ObjectNode knownLimitations = (ObjectNode) findSection(candidate, "KNOWN_LIMITATIONS");
		ArrayNode limitationBlocks = (ArrayNode) knownLimitations.path("blocks");
		JsonNode disclosureBlock = limitationBlocks.get(0);
		limitationBlocks.removeAll();
		ObjectNode websiteOverview = (ObjectNode) findSection(candidate, "WEBSITE_OVERVIEW");
		((ArrayNode) websiteOverview.path("blocks")).add(disclosureBlock);

		DocumentationCandidateValidationResult result = validator.validate(candidate.toString(), context, profile);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("disclosure"));
	}

	// -- context seeding --

	private DocumentationContext seedContext(boolean withFinding) {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH");
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		if (withFinding) {
			CandidateFinding finding = seedFinding(qaResult, candidate);
			seedPolicyEvaluation(qaResult, candidate, finding, "ALLOW");
		}
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		return contextAssembler.assemble(projectId, candidate, qaResult, profile, "de-AT", List.of());
	}

	private JsonNode firstDisclosureEntry(DocumentationContext context) {
		JsonNode content = objectMapper.readTree(context.getContentJson());
		return content.path("findingDisclosureView").path("entries").get(0);
	}

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

	// -- candidate JSON building --

	private ObjectNode buildFullValidCandidate(boolean withDisclosedFinding, String disclosureKey, String findingAuthorityKey) {
		ObjectNode document = objectMapper.createObjectNode();
		document.put("documentType", "CUSTOMER_WEBSITE_HANDOVER");
		ArrayNode sections = objectMapper.createArrayNode();

		sections.add(sectionWithOneClaim("WEBSITE_OVERVIEW", "C_WEBSITE_OVERVIEW", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("WEBSITE_STRUCTURE", "C_WEBSITE_STRUCTURE", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("FEATURES", "C_FEATURES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("CONTENT_AND_LANGUAGES", "C_CONTENT_AND_LANGUAGES", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));
		sections.add(sectionWithOneClaim("INTEGRATIONS", "C_INTEGRATIONS", "CUSTOMER_FACT", AUTH_IMPLEMENTATION_SUMMARY));

		if (withDisclosedFinding) {
			ObjectNode limitationClaim = claimNode("C_KNOWN_LIMITATION", "KNOWN_LIMITATION", List.of(findingAuthorityKey), List.of(disclosureKey));
			sections.add(sectionWithBlocks("KNOWN_LIMITATIONS", narrativeBlock(limitationClaim)));
		} else {
			sections.add(emptySection("KNOWN_LIMITATIONS"));
		}

		ObjectNode qaStatusClaim = claimNode("C_QA_STATUS", "QA_STATUS", List.of(AUTH_FULL_RELEASE_GATE), null);
		ObjectNode approvalClaim = claimNode("C_APPROVAL_STATUS", "APPROVAL_STATUS", List.of(AUTH_CTX_APPROVAL_RECORD), null);
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
		ObjectNode claim = claimNode(claimKey, claimType, List.of(authorityKey), null);
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

	private ObjectNode claimNode(String claimKey, String claimType, List<String> authorityKeys, List<String> disclosureKeys) {
		ObjectNode claim = objectMapper.createObjectNode();
		claim.put("claimKey", claimKey);
		claim.put("claimType", claimType);
		claim.put("derivation", "DIRECT");
		claim.put("text", "Example claim text.");
		ArrayNode authorityKeysArray = objectMapper.createArrayNode();
		authorityKeys.forEach(authorityKeysArray::add);
		claim.set("authorityKeys", authorityKeysArray);
		if (disclosureKeys != null) {
			ArrayNode disclosureKeysArray = objectMapper.createArrayNode();
			disclosureKeys.forEach(disclosureKeysArray::add);
			claim.set("disclosureKeys", disclosureKeysArray);
		}
		return claim;
	}

	// -- candidate JSON navigation helpers for negative-test mutation --

	private JsonNode findSection(ObjectNode candidate, String sectionType) {
		for (JsonNode section : candidate.path("documents").get(0).path("sections")) {
			if (sectionType.equals(section.path("sectionType").asString())) {
				return section;
			}
		}
		throw new IllegalStateException("section not found: " + sectionType);
	}

	private void swapSections(ObjectNode candidate, String sectionTypeA, String sectionTypeB) {
		ArrayNode sections = (ArrayNode) candidate.path("documents").get(0).path("sections");
		int indexA = -1;
		int indexB = -1;
		for (int i = 0; i < sections.size(); i++) {
			String sectionType = sections.get(i).path("sectionType").asString();
			if (sectionTypeA.equals(sectionType)) {
				indexA = i;
			}
			if (sectionTypeB.equals(sectionType)) {
				indexB = i;
			}
		}
		JsonNode a = sections.get(indexA);
		JsonNode b = sections.get(indexB);
		sections.set(indexA, b);
		sections.set(indexB, a);
	}

	private JsonNode findFirstClaimInSection(ObjectNode candidate, String sectionType) {
		JsonNode section = findSection(candidate, sectionType);
		return section.path("blocks").get(0).path("claims").get(0);
	}

	private JsonNode findFirstClaimOfType(ObjectNode candidate, String claimType) {
		for (JsonNode section : candidate.path("documents").get(0).path("sections")) {
			for (JsonNode block : section.path("blocks")) {
				for (JsonNode claim : block.path("claims")) {
					if (claimType.equals(claim.path("claimType").asString())) {
						return claim;
					}
				}
			}
		}
		throw new IllegalStateException("no claim of type " + claimType + " found");
	}
}
