package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * AIW-213's QA-side counterpart to {@link DeveloperExecutionInputAssembler}: materializes a real
 * {@code qa-execution-input.v1} JSON payload for one already-persisted, already-accepted {@link
 * WebsiteImplementationCandidate}, the single input artifact {@link
 * ai.architech.backend.core.agent.AgentDefinitionLoader} resolves for {@code website-qa-agent}
 * (its own {@code agent.yaml} declares exactly one required input artifact, {@code
 * qa-execution-input}, the same "prior canonical artifact" shape {@code
 * DeveloperExecutionInputAssembler} already composes for {@code developer-agent}).
 *
 * <p>Resolves {@code productAuthority.customerProfileRef}/{@code websiteRequirementsRef} from the
 * project's own latest canonical {@code customer-profile}/{@code website-requirements} artifact
 * versions - the same {@code ArtifactRepository}/{@code ArtifactVersionRepository} "latest
 * version" lookup {@link DeveloperExecutionInputAssembler#latestVersion} already establishes.
 * {@code sourceDesignRef}/{@code runtimeProfileRef} come directly from the Candidate's own real
 * bindings ({@link WebsiteImplementationCandidate#getSourceDesignRef()}/{@link
 * WebsiteImplementationCandidate#getRuntimeProfileRef()}) - never re-derived or re-guessed, so
 * {@link ai.architech.backend.core.validation.QAExecutionPreflightValidator}'s own
 * product-authority-binding check trivially holds for every payload this class produces.
 * {@code integrationContractRefs} is always empty in V1, the same documented "no Developer-safe
 * Integration Contract projection exists yet" scope {@link
 * DeveloperExecutionInputAssembler#integrationContext()} already carries for the Developer side.
 *
 * <p>{@code qaAuthority}/{@code executionContext.toolCapabilityProfileRef} are this V1 epic's own
 * frozen, single-version refs - literally copied (not referenced, since {@link
 * ai.architech.backend.core.validation.QAExecutionPreflightValidator}'s own constants are
 * package-private in a different package) from that validator's own {@code VALID_QA_PROFILE_REFS}/
 * {@code EXPECTED_*} fields, so a payload this class assembles always passes that validator's
 * exact-match checks by construction. {@code executionContext.executionSurfaceRef}/{@code
 * safeTestContextRef}/{@code browserContextRef} are deliberately omitted (all optional per {@code
 * qa-execution-input.schema.json}) - no durable, re-visitable Preview surface provisioning
 * infrastructure exists anywhere in this codebase yet, the exact same real, honest gap {@code
 * QAExecutionPreflightValidator}'s own javadoc already names rather than inventing a fake surface
 * reference to paper over it.
 *
 * <p><b>{@code qaExecutionRef}/{@code provenance.inputSnapshotRef} are fresh {@code
 * UUID.randomUUID()} placeholders, not real row references</b> - verified deliberate, not an
 * oversight: {@link ai.architech.backend.core.validation.SemanticQaReviewOutputIdentityValidator}
 * only ever confirms a QA Agent's own result echoes back exactly what this execution's input
 * claimed (never resolves either ref against a real, already-persisted {@code QaExecution}/{@code
 * QaInputSnapshot} row), and the real rows for *this* execution don't exist yet at assembly time -
 * {@link QaSemanticReviewOrchestrator#run} itself is what creates them, strictly after this
 * payload has already been built and preflight-validated. A value generated here therefore never
 * needs to match a real row; it only ever needs to be echoed back unchanged, which this class's
 * own caller trivially guarantees by threading its own {@code qaExecutionInputJson} straight into
 * {@code QaSemanticReviewOrchestrator.run}.
 */
@Component
class QaExecutionInputAssembler {

	static final String CUSTOMER_PROFILE_TYPE = "customer-profile";
	static final String WEBSITE_REQUIREMENTS_TYPE = "website-requirements";

	// Copied literal-for-literal from QAExecutionPreflightValidator's own frozen V1 constants -
	// see this class's own javadoc for why they cannot be referenced directly.
	static final String QA_PROFILE_REF = "website-qa-full-release@1.0.0";
	static final String RULE_SET_REF = "website-qa-rules@1.0.0";
	static final String SKILL_SET_REF = "website-qa-skills@1.0.0";
	static final String VALIDATOR_SET_REF = "website-qa-validator-set@1.0.0";
	static final String FINDING_TAXONOMY_REF = "website-qa-finding-taxonomy@1.0.0";
	static final String CHECK_REGISTRY_REF = "website-qa-check-registry@1.0.0";
	static final String TOOL_CAPABILITY_PROFILE_REF = "website-qa-tools@1.0.0";

	// This V1 epic's own frozen QA Agent contract version - matches the "1.0.0" convention every
	// existing qa-execution-input fixture already uses for provenance.qaAgentVersion, distinct
	// from website-qa-agent's own agent.yaml "version: 1" AgentDefinitionLoader resolution number.
	static final String QA_AGENT_VERSION = "1.0.0";

	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final ObjectMapper objectMapper;

	QaExecutionInputAssembler(
			ArtifactRepository artifactRepository, ArtifactVersionRepository artifactVersionRepository, ObjectMapper objectMapper) {
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.objectMapper = objectMapper;
	}

	String assemble(UUID projectId, WebsiteImplementationCandidate candidate) {
		ArtifactVersion customerProfile = latestVersion(projectId, CUSTOMER_PROFILE_TYPE);
		ArtifactVersion websiteRequirements = latestVersion(projectId, WEBSITE_REQUIREMENTS_TYPE);

		ObjectNode root = objectMapper.createObjectNode();
		root.put("schemaVersion", "1.0.0");
		root.put("qaExecutionRef", UUID.randomUUID().toString());
		root.set("target", target(candidate));
		root.set("productAuthority", productAuthority(customerProfile, websiteRequirements, candidate));
		root.set("qaAuthority", qaAuthority());
		root.set("executionContext", executionContext());
		root.set("provenance", provenance());

		return objectMapper.writeValueAsString(root);
	}

	private ObjectNode target(WebsiteImplementationCandidate candidate) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("candidateRef", candidate.getId().toString());
		return node;
	}

	private ObjectNode productAuthority(
			ArtifactVersion customerProfile, ArtifactVersion websiteRequirements, WebsiteImplementationCandidate candidate) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("customerProfileRef", customerProfile.getId().toString());
		node.put("websiteRequirementsRef", websiteRequirements.getId().toString());
		node.put("sourceDesignRef", candidate.getSourceDesignRef());
		node.put("runtimeProfileRef", candidate.getRuntimeProfileRef());
		node.set("integrationContractRefs", objectMapper.createArrayNode());
		return node;
	}

	private ObjectNode qaAuthority() {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("qaProfileRef", QA_PROFILE_REF);
		node.put("ruleSetRef", RULE_SET_REF);
		node.put("skillSetRef", SKILL_SET_REF);
		node.put("validatorSetRef", VALIDATOR_SET_REF);
		node.put("findingTaxonomyRef", FINDING_TAXONOMY_REF);
		node.put("checkRegistryRef", CHECK_REGISTRY_REF);
		return node;
	}

	private ObjectNode executionContext() {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("toolCapabilityProfileRef", TOOL_CAPABILITY_PROFILE_REF);
		return node;
	}

	private ObjectNode provenance() {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("inputSnapshotRef", UUID.randomUUID().toString());
		node.put("qaAgentVersion", QA_AGENT_VERSION);
		return node;
	}

	private ArtifactVersion latestVersion(UUID projectId, String type) {
		Artifact artifact = artifactRepository
				.findByProjectIdAndType(projectId, type)
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.CANONICAL_ARTIFACT_NOT_FOUND, "No canonical '" + type + "' artifact exists yet for project " + projectId));
		return artifactVersionRepository
				.findTopByArtifactIdOrderByVersionNumberDesc(artifact.getId())
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.CANONICAL_ARTIFACT_NOT_FOUND, "Artifact '" + type + "' for project " + projectId + " has no version yet"));
	}
}
