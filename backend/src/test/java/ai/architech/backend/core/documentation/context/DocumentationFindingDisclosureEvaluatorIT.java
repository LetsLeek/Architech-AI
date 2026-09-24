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

/**
 * Real-Postgres proof of AIW-192's {@link DocumentationFindingDisclosureEvaluator}, exercised
 * through {@link DocumentationContextAssembler#assemble} end-to-end so every assertion is against
 * a real, schema-validated {@code documentation-context.v1} payload - the zero-findings/customer
 * case (proving {@code NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED} gets emitted) is already covered
 * by {@link DocumentationContextAssemblerIT}'s own first test, updated by this ticket; this class
 * covers the remaining materiality-disposition paths.
 */
@SpringBootTest
@Transactional
class DocumentationFindingDisclosureEvaluatorIT {

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
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private PolicyEvaluationRepository policyEvaluationRepository;

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
	void discloseAnAllowDisposedFindingToCustomerWithAMatchingCatalogEntry() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		CandidateFinding finding = seedFinding(qaResult, candidate);
		seedPolicyEvaluation(qaResult, candidate, finding, "ALLOW");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		DocumentationContext context = assembler.assemble(projectId, candidate, qaResult, profile, "de-AT", List.of());

		SchemaValidationResult result = schemaRegistry.validate(CONTEXT_SCHEMA_URN, context.getContentJson());
		assertThat(result.valid()).as(result.issues().toString()).isTrue();

		JsonNode content = objectMapper.readTree(context.getContentJson());
		List<JsonNode> entries = stream(content.path("findingDisclosureView").path("entries")).toList();
		assertThat(entries).hasSize(1);
		JsonNode entry = entries.get(0);
		assertThat(entry.path("action").asString()).isEqualTo("DISCLOSE");
		assertThat(entry.path("audience").asString()).isEqualTo("CUSTOMER");
		assertThat(entry.path("reasonCode").asString()).isEqualTo("MATERIAL_CUSTOMER_IMPACT");

		String findingAuthorityKey = entry.path("findingAuthorityKey").asString();
		List<String> catalogKeys = stream(content.path("authorityCatalog")).map(n -> n.path("key").asString()).toList();
		assertThat(catalogKeys).contains(findingAuthorityKey);

		List<String> stateKeys = stream(content.path("contextStates")).map(n -> n.path("stateKey").asString()).toList();
		assertThat(stateKeys).doesNotContain("CTX_NO_CUSTOMER_DISCLOSABLE_FINDINGS");
	}

	@Test
	void blocksAssemblyWhenACustomerAudienceFindingIsBlockDisposed() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "PASS");
		CandidateFinding finding = seedFinding(qaResult, candidate);
		seedPolicyEvaluation(qaResult, candidate, finding, "BLOCK");
		DocumentationProfile profile = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");

		long before = contextRepository.count();

		assertThatThrownBy(() -> assembler.assemble(projectId, candidate, qaResult, profile, "de-AT", List.of()))
				.isInstanceOf(DocumentationFindingDisclosureBlockedException.class)
				.hasMessageContaining(finding.getId().toString())
				.hasMessageContaining("BLOCK");

		assertThat(contextRepository.count()).isEqualTo(before);
	}

	@Test
	void discloseEveryFindingToDeveloperRegardlessOfDispositionEvenOnHoldGate() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		CandidateFinding allowFinding = seedFinding(qaResult, candidate);
		seedPolicyEvaluation(qaResult, candidate, allowFinding, "ALLOW");
		CandidateFinding blockFinding = seedFinding(qaResult, candidate);
		seedPolicyEvaluation(qaResult, candidate, blockFinding, "BLOCK");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");

		DocumentationContext context = assembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());

		SchemaValidationResult result = schemaRegistry.validate(CONTEXT_SCHEMA_URN, context.getContentJson());
		assertThat(result.valid()).as(result.issues().toString()).isTrue();

		JsonNode content = objectMapper.readTree(context.getContentJson());
		List<JsonNode> entries = stream(content.path("findingDisclosureView").path("entries")).toList();
		assertThat(entries).hasSize(2);
		assertThat(entries).allSatisfy(entry -> {
			assertThat(entry.path("action").asString()).isEqualTo("DISCLOSE");
			assertThat(entry.path("audience").asString()).isEqualTo("DEVELOPER");
			assertThat(entry.path("reasonCode").asString()).isEqualTo("ALL_TECHNICALLY_RELEVANT_CURRENT");
		});

		List<String> stateKeys = stream(content.path("contextStates")).map(n -> n.path("stateKey").asString()).toList();
		assertThat(stateKeys).doesNotContain("CTX_NO_CUSTOMER_DISCLOSABLE_FINDINGS");
	}

	@Test
	void developerAudienceWithZeroFindingsGetsNoNoFindContextState() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		WebsiteImplementationCandidate candidate = seedCandidateWithDesign(projectId, "prop-a");
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");

		DocumentationContext context = assembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());

		SchemaValidationResult result = schemaRegistry.validate(CONTEXT_SCHEMA_URN, context.getContentJson());
		assertThat(result.valid()).as(result.issues().toString()).isTrue();

		JsonNode content = objectMapper.readTree(context.getContentJson());
		assertThat(content.path("findingDisclosureView").path("entries").size()).isZero();

		List<String> stateKeys = stream(content.path("contextStates")).map(n -> n.path("stateKey").asString()).toList();
		assertThat(stateKeys).doesNotContain("CTX_NO_CUSTOMER_DISCLOSABLE_FINDINGS");
	}

	private Stream<JsonNode> stream(JsonNode array) {
		Stream.Builder<JsonNode> builder = Stream.builder();
		array.forEach(builder::add);
		return builder.build();
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

	private WebsiteImplementationCandidate seedCandidateWithDesign(UUID projectId, String proposalLocalRef) {
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
}
