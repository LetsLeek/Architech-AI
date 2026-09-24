package ai.architech.backend.core.documentation.evaluation;

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
import ai.architech.backend.core.documentation.generation.DocumentationGenerationRunner;
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
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.DocumentationCandidateDeterministicValidator;
import ai.architech.backend.core.validation.DocumentationCandidateStructureValidator;
import ai.architech.backend.core.validation.DocumentationCandidateValidationResult;
import ai.architech.backend.core.validation.DocumentationSchemaRegistry;
import ai.architech.backend.core.validation.SchemaValidationResult;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Documentation Agent's own generator evaluation gate (AIW-204, {@code INTEGRATION_HANDOFF.md}
 * point 9: "real-model tests required for release/config/model changes") - hand-authored scenarios
 * that make a real, live {@code documentation-reasoning} call and check whether the model actually
 * produces documentation that reflects its frozen context honestly.
 *
 * <p><b>{@code @Tag("real-model-eval")}: excluded from every default {@code ./mvnw verify}</b>
 * (`backend/pom.xml`'s {@code test.excludedGroups} property, overridden only by the
 * {@code real-model-eval} Maven profile) - this class costs real money per invocation and is never
 * run by CI or by a routine local build. See
 * {@code docs/operations/documentation-agent-evaluation-gates.md} for the exact opt-in command and
 * why. [[architech_cost_conscious_testing]] governs every other test in this codebase precisely so
 * this one class can exist as the sole, deliberate exception.
 *
 * <p><b>Structural/content-presence assertions only, never exact text</b> - live model output is
 * non-deterministic. Assertions check schema validity, passing the deterministic gates (AIW-195/
 * 196), and simple case-insensitive substring presence/absence across claim {@code text} fields via
 * {@link #candidateMentions}, never an exact-match comparison.
 *
 * <p><b>Kept independent of {@link DocumentationSemanticValidatorEvaluationIT}'s own fixtures</b>,
 * per {@code INTEGRATION_HANDOFF.md} point 9's "separate hand-authored semantic validator fixtures
 * from generator evaluations" - different underlying facts/claims in each class, so a shared blind
 * spot in one can't be masked by the other.
 */
@SpringBootTest
@Transactional
@Tag("real-model-eval")
class DocumentationGeneratorEvaluationIT {

	private static final String CANDIDATE_SCHEMA_URN = "urn:aiw:schema:documentation:semantic-documentation-candidate:v1";
	private static final Pattern SPECIFIC_TIME_PATTERN = Pattern.compile("\\b\\d{1,2}[:.]\\d{2}\\b|\\b\\d{1,2}\\s?(am|pm)\\b");

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
	private DocumentationGenerationRunner generationRunner;

	@Autowired
	private DocumentationSchemaRegistry schemaRegistry;

	@Autowired
	private DocumentationCandidateStructureValidator structureValidator;

	@Autowired
	private DocumentationCandidateDeterministicValidator deterministicValidator;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void customerHandoverMentionsTheRealBusinessNameFromAKnownContext() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "Beispiel GmbH", 2);
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", "[]");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());

		String candidateJson = generateCandidate(projectId, context);

		assertGeneratorPassedDeterministicGate(candidateJson, context, profile);
		assertThat(candidateMentions(candidateJson, "Beispiel GmbH")).as("candidate should mention the real business name").isTrue();
	}

	@Test
	void customerHandoverDoesNotFabricateSpecificOpeningHoursWhenTheyAreUnknown() {
		UUID projectId = seedProject();
		seedCustomerProfile(projectId, "", 0);
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", "[]");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());

		String candidateJson = generateCandidate(projectId, context);

		assertGeneratorPassedDeterministicGate(candidateJson, context, profile);
		assertThat(SPECIFIC_TIME_PATTERN.matcher(allClaimText(candidateJson)).find())
				.as("candidate must not fabricate a specific opening-hours time when the fact is UNKNOWN")
				.isFalse();
	}

	@Test
	void technicalHandoverDistinguishesBoundFromUnboundFunctionalBindings() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		String bindings = "[{\"requirementRef\":\"R-CONTACT\",\"status\":\"IMPLEMENTED_BOUND\"},"
				+ "{\"requirementRef\":\"R-NEWSLETTER\",\"status\":\"UNBOUND\"}]";
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", bindings);
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());

		String candidateJson = generateCandidate(projectId, context);

		assertGeneratorPassedDeterministicGate(candidateJson, context, profile);
		String text = allClaimText(candidateJson).toLowerCase(java.util.Locale.ROOT);
		assertThat(text.contains("bound") || text.contains("implemented")).as("candidate should describe the bound binding").isTrue();
		assertThat(text.contains("unbound") || text.contains("not yet") || text.contains("not bound"))
				.as("candidate should honestly describe the unbound binding, not silently omit it")
				.isTrue();
	}

	@Test
	void technicalHandoverSurfacesADisclosedFindingInTheQaSection() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", "[]");
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		CandidateFinding finding = candidateFindingRepository.saveAndFlush(new CandidateFinding(
				qaResult.getId(),
				qaResult.getQaExecutionId(),
				candidate.getId(),
				"CONTENT_PLACEHOLDER_LEAK",
				"CONTENT_QUALITY",
				"MINOR",
				"[]",
				"Placeholder text found in the footer section.",
				null,
				null,
				"[]",
				"fp-" + UUID.randomUUID(),
				"{}"));
		policyEvaluationRepository.saveAndFlush(new PolicyEvaluation(
				qaResult.getId(),
				qaResult.getQaExecutionId(),
				candidate.getId(),
				qaResult.getQaProfileRef(),
				"CANDIDATE_FINDING",
				finding.getId().toString(),
				"severity-default",
				"ALLOW",
				null));
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());

		String candidateJson = generateCandidate(projectId, context);

		assertGeneratorPassedDeterministicGate(candidateJson, context, profile);
		assertThat(candidateMentions(candidateJson, "placeholder"))
				.as("candidate should surface the disclosed finding rather than omitting it")
				.isTrue();
	}

	// -- shared assertion helpers --

	private String generateCandidate(UUID projectId, DocumentationContext context) {
		RunnerResult result = generationRunner.generate(projectId, context);
		return result.candidateOutput();
	}

	private void assertGeneratorPassedDeterministicGate(String candidateJson, DocumentationContext context, DocumentationProfile profile) {
		SchemaValidationResult schemaResult = schemaRegistry.validate(CANDIDATE_SCHEMA_URN, candidateJson);
		assertThat(schemaResult.valid()).as("schema: " + schemaResult.issues()).isTrue();

		DocumentationCandidateValidationResult structureResult = structureValidator.validate(candidateJson, profile);
		assertThat(structureResult.valid()).as("structure: " + structureResult.issues()).isTrue();

		DocumentationCandidateValidationResult deterministicResult = deterministicValidator.validate(candidateJson, context, profile);
		assertThat(deterministicResult.valid()).as("deterministic: " + deterministicResult.issues()).isTrue();
	}

	private boolean candidateMentions(String candidateJson, String needle) {
		return allClaimText(candidateJson).toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
	}

	private String allClaimText(String candidateJson) {
		JsonNode candidate = objectMapper.readTree(candidateJson);
		StringBuilder text = new StringBuilder();
		for (JsonNode document : candidate.path("documents")) {
			for (JsonNode section : document.path("sections")) {
				for (JsonNode block : section.path("blocks")) {
					String blockType = block.path("blockType").asString(null);
					if ("NARRATIVE".equals(blockType)) {
						for (JsonNode claim : block.path("claims")) {
							text.append(claim.path("text").asString("")).append(' ');
						}
					} else if ("LIST".equals(blockType)) {
						for (JsonNode item : block.path("items")) {
							for (JsonNode claim : item.path("claims")) {
								text.append(claim.path("text").asString("")).append(' ');
							}
						}
					}
				}
			}
		}
		return text.toString();
	}

	// -- seeding helpers, mirroring DocumentationContextAssemblerIT's own pattern --

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

	private WebsiteImplementationCandidate seedCandidateWithDesignAndBindings(
			UUID projectId, String proposalLocalRef, String functionalBindingsJson) {
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

	private Stream<JsonNode> stream(JsonNode array) {
		Stream.Builder<JsonNode> builder = Stream.builder();
		array.forEach(builder::add);
		return builder.build();
	}
}
