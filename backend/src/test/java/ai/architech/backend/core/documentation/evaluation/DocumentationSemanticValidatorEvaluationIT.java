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
import ai.architech.backend.core.validation.DocumentationFactualConsistencyValidator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Documentation Agent's own semantic-validator evaluation gate (AIW-204) - hand-authored
 * (claim + cited fact + counterfact) tuples with a known, unambiguous correct verdict, checked
 * against a real, live {@code documentation-factual-consistency} call to
 * {@link DocumentationFactualConsistencyValidator}.
 *
 * <p><b>{@code @Tag("real-model-eval")}</b>: same exclusion-from-every-default-build mechanism as
 * {@link DocumentationGeneratorEvaluationIT} - see that class's own javadoc and
 * {@code docs/operations/documentation-agent-evaluation-gates.md}.
 *
 * <p><b>Independent fixtures from {@link DocumentationGeneratorEvaluationIT}</b>, per
 * {@code INTEGRATION_HANDOFF.md} point 9 - claims here are hand-written directly (never taken from
 * a live generator call), citing real authority/disclosure keys extracted from a real assembled
 * {@link DocumentationContext} so the keys genuinely resolve, but the claim *text* and its expected
 * verdict are this class's own fixed, known-correct fixtures.
 */
@SpringBootTest
@Transactional
@Tag("real-model-eval")
class DocumentationSemanticValidatorEvaluationIT {

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
	private DocumentationFactualConsistencyValidator factualConsistencyValidator;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void anAccurateClaimCitingTheRealQaGateIsSupported() {
		Fixture fixture = seedTechnicalFixtureWithBoundAndUnboundBindings();
		String qaGateAuthorityKey = findAuthorityKeyByFactType(fixture.context, "FULL_RELEASE_GATE");

		String candidateJson = oneClaimCandidate(
				"C-QA-1",
				"QA_STATUS",
				"This website has passed full release quality assurance.",
				List.of(qaGateAuthorityKey),
				List.of());

		List<JsonNode> issues = factualConsistencyValidator.validate(candidateJson, fixture.context, fixture.profile);

		assertThat(issues).as("an accurate, directly-supported claim should have no issues: " + issues).isEmpty();
	}

	@Test
	void aClaimThatIgnoresAnUnboundSiblingBindingIsUnsupported() {
		Fixture fixture = seedTechnicalFixtureWithBoundAndUnboundBindings();
		String boundAuthorityKey = findAuthorityKeyByFactType(fixture.context, "BINDING_STATE", "IMPLEMENTED_BOUND");

		// Cites only the accurately-bound contact-form binding's own authority, but the claim text
		// itself cherry-picks by asserting *all* integrations (including the actually-unbound
		// newsletter signup) are fully implemented and bound.
		String candidateJson = oneClaimCandidate(
				"C-BIND-1",
				"INTEGRATION_DESCRIPTION",
				"All requested integrations, including the newsletter signup, are fully implemented and bound.",
				List.of(boundAuthorityKey),
				List.of());

		List<JsonNode> issues = factualConsistencyValidator.validate(candidateJson, fixture.context, fixture.profile);

		assertThat(issues).as("a claim cherry-picking a bound citation while ignoring an unbound sibling should be flagged").isNotEmpty();
		assertThat(issues).allMatch(issue -> "EVALUATION_ISSUE".equals(issue.path("issueClass").asString(null)));
	}

	@Test
	void aClaimThatSoftensADisclosedCriticalFindingIsUnsupported() {
		Fixture fixture = seedTechnicalFixtureWithBoundAndUnboundBindings();
		CandidateFinding finding = candidateFindingRepository.saveAndFlush(new CandidateFinding(
				fixture.qaResult.getId(),
				fixture.qaResult.getQaExecutionId(),
				fixture.candidate.getId(),
				"SEO_REQUIRED_TITLE_MISSING",
				"SEO_METADATA",
				"CRITICAL",
				"[]",
				"Required page title metadata is missing on the homepage.",
				null,
				null,
				"[]",
				"fp-" + UUID.randomUUID(),
				"{}"));
		policyEvaluationRepository.saveAndFlush(new PolicyEvaluation(
				fixture.qaResult.getId(),
				fixture.qaResult.getQaExecutionId(),
				fixture.candidate.getId(),
				fixture.qaResult.getQaProfileRef(),
				"CANDIDATE_FINDING",
				finding.getId().toString(),
				"severity-default",
				"ALLOW",
				null));
		DocumentationContext contextWithFinding =
				contextAssembler.assemble(fixture.projectId, fixture.candidate, fixture.qaResult, fixture.profile, "en-GB", List.of());
		JsonNode disclosureEntry = firstDisclosureEntry(contextWithFinding);
		String disclosureKey = disclosureEntry.path("disclosureKey").asString();
		String findingAuthorityKey = disclosureEntry.path("findingAuthorityKey").asString();

		String candidateJson = oneClaimCandidateWithDisclosure(
				"C-FIND-1",
				"KNOWN_LIMITATION",
				"There is a very minor, purely cosmetic detail that does not affect the website in any way.",
				List.of(findingAuthorityKey),
				List.of(disclosureKey));

		List<JsonNode> issues = factualConsistencyValidator.validate(candidateJson, contextWithFinding, fixture.profile);

		assertThat(issues).as("softening a disclosed CRITICAL finding's real impact should be flagged").isNotEmpty();
	}

	// -- fixture construction --

	private record Fixture(UUID projectId, WebsiteImplementationCandidate candidate, QaResult qaResult, DocumentationProfile profile, DocumentationContext context) {}

	private Fixture seedTechnicalFixtureWithBoundAndUnboundBindings() {
		UUID projectId = seedProject();
		seedCanonicalArtifact(projectId, "website-requirements");
		String bindings = "[{\"requirementRef\":\"R-CONTACT\",\"status\":\"IMPLEMENTED_BOUND\"},"
				+ "{\"requirementRef\":\"R-NEWSLETTER\",\"status\":\"UNBOUND\"}]";
		WebsiteImplementationCandidate candidate = seedCandidateWithDesignAndBindings(projectId, "prop-a", bindings);
		QaResult qaResult = seedQaResult(candidate, "HOLD");
		DocumentationProfile profile = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		DocumentationContext context = contextAssembler.assemble(projectId, candidate, qaResult, profile, "en-GB", List.of());
		return new Fixture(projectId, candidate, qaResult, profile, context);
	}

	private String findAuthorityKeyByFactType(DocumentationContext context, String factType) {
		return findAuthorityKeyByFactType(context, factType, null);
	}

	private String findAuthorityKeyByFactType(DocumentationContext context, String factType, String requiredValue) {
		JsonNode content = objectMapper.readTree(context.getContentJson());
		String factKey = null;
		for (JsonNode fact : content.path("resolvedFacts")) {
			if (!factType.equals(fact.path("factType").asString(null))) {
				continue;
			}
			if (requiredValue != null && !requiredValue.equals(fact.path("value").path("value").asString(null))) {
				continue;
			}
			factKey = fact.path("factKey").asString(null);
			break;
		}
		if (factKey == null) {
			throw new IllegalStateException("No resolved fact of type " + factType + " found in assembled context");
		}
		for (JsonNode entry : content.path("authorityCatalog")) {
			for (JsonNode key : entry.path("safeFactKeys")) {
				if (factKey.equals(key.asString())) {
					return entry.path("key").asString();
				}
			}
		}
		throw new IllegalStateException("No authorityCatalog entry references fact key " + factKey);
	}

	private JsonNode firstDisclosureEntry(DocumentationContext context) {
		JsonNode content = objectMapper.readTree(context.getContentJson());
		JsonNode entries = content.path("findingDisclosureView").path("entries");
		if (!entries.isArray() || entries.isEmpty()) {
			throw new IllegalStateException("Assembled context has no findingDisclosureView entries");
		}
		return entries.get(0);
	}

	private String oneClaimCandidate(String claimKey, String claimType, String text, List<String> authorityKeys, List<String> disclosureKeys) {
		return disclosureKeys.isEmpty()
				? buildCandidate(claimKey, claimType, text, authorityKeys, null)
				: oneClaimCandidateWithDisclosure(claimKey, claimType, text, authorityKeys, disclosureKeys);
	}

	private String oneClaimCandidateWithDisclosure(
			String claimKey, String claimType, String text, List<String> authorityKeys, List<String> disclosureKeys) {
		return buildCandidate(claimKey, claimType, text, authorityKeys, disclosureKeys);
	}

	private String buildCandidate(String claimKey, String claimType, String text, List<String> authorityKeys, List<String> disclosureKeys) {
		var claim = objectMapper.createObjectNode();
		claim.put("claimKey", claimKey);
		claim.put("claimType", claimType);
		claim.put("derivation", "DIRECT");
		claim.put("text", text);
		var authorityKeysNode = objectMapper.createArrayNode();
		authorityKeys.forEach(authorityKeysNode::add);
		claim.set("authorityKeys", authorityKeysNode);
		if (disclosureKeys != null && !disclosureKeys.isEmpty()) {
			var disclosureKeysNode = objectMapper.createArrayNode();
			disclosureKeys.forEach(disclosureKeysNode::add);
			claim.set("disclosureKeys", disclosureKeysNode);
		}

		var block = objectMapper.createObjectNode();
		block.put("blockType", "NARRATIVE");
		var claims = objectMapper.createArrayNode();
		claims.add(claim);
		block.set("claims", claims);

		var section = objectMapper.createObjectNode();
		section.put("sectionType", "QA_AND_OUTSTANDING_ISSUES");
		var blocks = objectMapper.createArrayNode();
		blocks.add(block);
		section.set("blocks", blocks);

		var document = objectMapper.createObjectNode();
		document.put("documentType", "TECHNICAL_HANDOVER_GUIDE");
		var sections = objectMapper.createArrayNode();
		sections.add(section);
		document.set("sections", sections);

		var root = objectMapper.createObjectNode();
		root.put("schemaVersion", "1.0.0");
		var documents = objectMapper.createArrayNode();
		documents.add(document);
		root.set("documents", documents);

		return root.toString();
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
}
