package ai.architech.backend.core.documentation.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
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
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.validation.DocumentationSchemaRegistry;
import ai.architech.backend.core.validation.SchemaValidationResult;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real-Postgres proof of AIW-190's assembler: the assembled {@code documentation-context.v1}
 * payload actually validates against the frozen schema family (AIW-187's {@link
 * DocumentationSchemaRegistry}), not just against this ticket's own prose description of the
 * shape - the exact class of mistake AIW-189 made and this ticket had to fix.
 */
@SpringBootTest
@Transactional
class DocumentationContextAssemblerIT {

	private static final String CONTEXT_SCHEMA_URN = "urn:aiw:schema:documentation:documentation-context:v1";

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
	private DocumentationContextAssembler assembler;

	@Autowired
	private DocumentationContextRepository contextRepository;

	@Autowired
	private DocumentationSchemaRegistry schemaRegistry;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void assemblesASchemaValidCustomerHandoverContextWithBusinessFactsAndContextStateProofs() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH", 2);
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate =
				seedCandidateWithDesignAndBindings(projectId, "prop-a", "[{\"requirementRef\":\"R1\",\"status\":\"IMPLEMENTED_BOUND\"}]");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		DocumentationContext context =
				assembler.assemble(projectId, candidate, qaResult, profile, "de-AT", emptyFindingDisclosureView(), List.of());

		JsonNode content = objectMapper.readTree(context.getContentJson());
		SchemaValidationResult result = schemaRegistry.validate(CONTEXT_SCHEMA_URN, context.getContentJson());
		assertThat(result.valid()).as(result.issues().toString()).isTrue();

		assertThat(context.getContextVersion()).isEqualTo(1);
		assertThat(contextRepository.findById(context.getId())).isPresent();
		assertThat(content.path("securityProjection").path("redactionApplied").asBoolean()).isTrue();
		assertThat(content.path("securityProjection").path("audienceMinimizationApplied").asBoolean()).isTrue();

		List<String> stateKeys = stream(content.path("contextStates")).map(n -> n.path("stateKey").asString()).toList();
		assertThat(stateKeys).containsExactlyInAnyOrder(
				"CTX_SELECTION_DECISION", "CTX_APPROVAL_RECORD", "CTX_DEPLOYMENT_RECORD", "CTX_SECTION_CHANGE_AND_MAINTENANCE");

		List<String> factTypes = stream(content.path("resolvedFacts")).map(n -> n.path("factType").asString()).toList();
		assertThat(factTypes).contains(
				"BUSINESS_NAME", "OPENING_HOURS", "BINDING_STATE", "FULL_RELEASE_GATE", "IMPLEMENTATION_SUMMARY");

		JsonNode businessNameFact =
				stream(content.path("resolvedFacts")).filter(n -> "BUSINESS_NAME".equals(n.path("factType").asString())).findFirst().orElseThrow();
		assertThat(businessNameFact.path("state").asString()).isEqualTo("KNOWN");
		assertThat(businessNameFact.path("value").path("value").asString()).isEqualTo("Beispiel GmbH");

		JsonNode openingHoursFact =
				stream(content.path("resolvedFacts")).filter(n -> "OPENING_HOURS".equals(n.path("factType").asString())).findFirst().orElseThrow();
		assertThat(openingHoursFact.path("state").asString()).isEqualTo("KNOWN");
		assertThat(openingHoursFact.path("value").path("value").asInt()).isEqualTo(2);
	}

	@Test
	void assemblesASchemaValidTechnicalHandoverContextWithNoCustomerProfileArtifact() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", "[]");
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");

		DocumentationContext context =
				assembler.assemble(projectId, candidate, qaResult, profile, "en-GB", emptyFindingDisclosureView(), List.of());

		JsonNode content = objectMapper.readTree(context.getContentJson());
		SchemaValidationResult result = schemaRegistry.validate(CONTEXT_SCHEMA_URN, context.getContentJson());
		assertThat(result.valid()).as(result.issues().toString()).isTrue();

		List<String> stateKeys = stream(content.path("contextStates")).map(n -> n.path("stateKey").asString()).toList();
		assertThat(stateKeys).containsExactlyInAnyOrder(
				"CTX_CUSTOMER_PROFILE", "CTX_SELECTION_DECISION", "CTX_APPROVAL_RECORD", "CTX_DEPLOYMENT_RECORD", "CTX_SECTION_MAINTENANCE_NOTES");

		List<String> factTypes = stream(content.path("resolvedFacts")).map(n -> n.path("factType").asString()).toList();
		assertThat(factTypes).doesNotContain("BUSINESS_NAME", "OPENING_HOURS");
	}

	@Test
	void marksBusinessNameUnknownWhenTheFieldIsBlank() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "", 0);
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", "[]");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		DocumentationContext context =
				assembler.assemble(projectId, candidate, qaResult, profile, "de-AT", emptyFindingDisclosureView(), List.of());

		JsonNode content = objectMapper.readTree(context.getContentJson());
		assertThat(schemaRegistry.validate(CONTEXT_SCHEMA_URN, context.getContentJson()).valid()).isTrue();

		JsonNode businessNameFact =
				stream(content.path("resolvedFacts")).filter(n -> "BUSINESS_NAME".equals(n.path("factType").asString())).findFirst().orElseThrow();
		assertThat(businessNameFact.path("state").asString()).isEqualTo("UNKNOWN");
		assertThat(businessNameFact.has("value")).isFalse();

		JsonNode openingHoursFact =
				stream(content.path("resolvedFacts")).filter(n -> "OPENING_HOURS".equals(n.path("factType").asString())).findFirst().orElseThrow();
		assertThat(openingHoursFact.path("state").asString()).isEqualTo("UNKNOWN");
	}

	@Test
	void blocksAssemblyWhenAGeneratedFactAccidentallyContainsASecretShape() {
		UUID projectId = seedProject();
		// AKIA + 16 alphanumerics: structurally matches SecretPatterns.AWS_ACCESS_KEY, but is an
		// unambiguous, obviously-fake test placeholder, never a real key.
		String fakeSecretLookingBusinessName = "AKIAFAKETESTKEY12345";
		seedCustomerProfile(projectId, fakeSecretLookingBusinessName, 0);
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", "[]");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		assertThatThrownBy(() -> assembler.assemble(
						projectId, candidate, qaResult, profile, "de-AT", emptyFindingDisclosureView(), List.of()))
				.isInstanceOf(DocumentationSecretLeakageDetectedException.class)
				.hasMessageContaining("AWS_ACCESS_KEY")
				.hasMessageNotContaining(fakeSecretLookingBusinessName);
	}

	@Test
	void truncatesAnOversizedImplementationSummaryRatherThanFailingSchemaValidation() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		String oversizedSummary = "x".repeat(5000);
		WebsiteImplementationCandidate candidate =
				seedCandidateWithDesignAndBindingsAndSummary(projectId, "prop-a", "[]", oversizedSummary);
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");

		DocumentationContext context =
				assembler.assemble(projectId, candidate, qaResult, profile, "en-GB", emptyFindingDisclosureView(), List.of());

		assertThat(schemaRegistry.validate(CONTEXT_SCHEMA_URN, context.getContentJson()).valid()).isTrue();

		JsonNode content = objectMapper.readTree(context.getContentJson());
		JsonNode summaryFact = stream(content.path("resolvedFacts"))
				.filter(n -> "IMPLEMENTATION_SUMMARY".equals(n.path("factType").asString()))
				.findFirst()
				.orElseThrow();
		assertThat(summaryFact.path("value").path("value").asString()).hasSize(4000);
	}

	private Stream<JsonNode> stream(JsonNode array) {
		Stream.Builder<JsonNode> builder = Stream.builder();
		array.forEach(builder::add);
		return builder.build();
	}

	private JsonNode emptyFindingDisclosureView() {
		ObjectNode node = objectMapper.createObjectNode();
		node.set("entries", objectMapper.createArrayNode());
		return node;
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
		artifactVersionRepository.saveAndFlush(
				new ArtifactVersion(artifact.getId(), 1, execution.getId(), "{\"content\":\"fixture\"}"));
	}

	private void seedCustomerProfile(UUID projectId, String businessName, int openingHoursCount) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, "customer-profile"));
		AgentExecution execution = seedSucceededExecution(projectId, "customer-profile-agent");

		ArrayNode openingHours = objectMapper.createArrayNode();
		for (int i = 0; i < openingHoursCount; i++) {
			ObjectNode entry = objectMapper.createObjectNode();
			entry.put("localRef", "oh-" + i);
			entry.put("raw", "Mon-Fri 9-17");
			openingHours.add(entry);
		}
		ObjectNode business = objectMapper.createObjectNode();
		if (!businessName.isBlank()) {
			business.put("name", businessName);
		}
		ObjectNode content = objectMapper.createObjectNode();
		content.set("business", business);
		content.set("openingHours", openingHours);

		artifactVersionRepository.saveAndFlush(
				new ArtifactVersion(artifact.getId(), 1, execution.getId(), objectMapper.writeValueAsString(content)));
	}

	private WebsiteImplementationCandidate seedCandidateWithDesignAndBindings(UUID projectId, String proposalLocalRef, String functionalBindingsJson) {
		return seedCandidateWithDesignAndBindingsAndSummary(projectId, proposalLocalRef, functionalBindingsJson, "summary");
	}

	private WebsiteImplementationCandidate seedCandidateWithDesignAndBindingsAndSummary(
			UUID projectId, String proposalLocalRef, String functionalBindingsJson, String implementationSummary) {
		Artifact designArtifact = artifactRepository
				.findByProjectIdAndType(projectId, "design-proposal-set")
				.orElseGet(() -> artifactRepository.saveAndFlush(new Artifact(projectId, "design-proposal-set")));
		int nextVersion = artifactVersionRepository.findByArtifactIdOrderByVersionNumberDesc(designArtifact.getId()).size() + 1;
		AgentExecution designExecution = seedSucceededExecution(projectId, "designer-agent");
		ArtifactVersion designVersion = artifactVersionRepository.saveAndFlush(new ArtifactVersion(
				designArtifact.getId(),
				nextVersion,
				designExecution.getId(),
				"{\"proposals\":[{\"localRef\":\"" + proposalLocalRef + "\"}]}"));

		AgentExecution developerExecution = seedSucceededExecution(projectId, "developer-agent");
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				designVersion.getId().toString(),
				proposalLocalRef,
				"runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(),
				implementationSummary,
				"[]",
				functionalBindingsJson,
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
}
