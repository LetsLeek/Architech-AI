package ai.architech.backend.core.validation;

import ai.architech.backend.core.agent.AgentDefinitionLoader;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.rule.RuleLoader;
import ai.architech.backend.core.skill.SkillLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AIW-141's Pre-Execution Validation for a {@code developer-execution-input}, run before any AI
 * invocation - matching {@code docs/VALIDATION-PIPELINE.md}'s own "invalid pre-execution state
 * prevents model invocation; it is a Core/validation failure, not a DeveloperBlocker" contract.
 *
 * <p><b>What this class validates</b> (everything buildable against this codebase's actual,
 * current infrastructure): strict schema validation ({@link DeveloperSchemaRegistry}); that
 * {@code customerProfileArtifactVersionRef}/{@code websiteRequirementsArtifactVersionRef}/
 * {@code designArtifactVersionRef} each resolve to a real, persisted {@link ArtifactVersion}
 * whose owning {@link Artifact} belongs to the target project and has the expected type (cross-
 * project reference / wrong-type rejection); that every embedded canonical payload
 * ({@code customerProfile}/{@code websiteRequirements}/the target {@code proposal}) matches the
 * exact content of its referenced artifact version byte-for-semantic-content, not a caller-
 * fabricated substitute; that {@code targetProposalLocalRef} actually exists inside the
 * referenced design-proposal-set; and that the frozen {@code developer-agent}/
 * {@code website-developer-integrity}/{@code website-developer} Agent/Rule/Skill definitions are
 * actually resolvable through the generic loaders.
 *
 * <p><b>What this class deliberately does not validate yet</b> (real, currently-missing
 * infrastructure this ticket does not retrofit): full upstream <em>lineage</em> - proving
 * {@code websiteRequirements} was actually derived from this exact {@code customerProfile}
 * version, and the target proposal from this exact {@code websiteRequirements} version, rather
 * than merely belonging to the same project - because no artifact carries any lineage/parent-
 * version reference anywhere in this codebase today (confirmed: {@link ArtifactVersion} has no
 * such field, for any project-type, not just Developer). Retrofitting that is a cross-cutting
 * data-model change affecting Requirements/Designer too, out of this ticket's own reasonable
 * scope. Also deferred: correction-allowance/retry-lineage validation (AIW-149/150's own not-
 * yet-built outcome/retry data model) and Developer-safe Integration Contract projection safety
 * (AIW-144's own not-yet-built projection - {@code developer-safe-integration-contract-view:v1}
 * is still AIW-133's placeholder). "Required baseline tool capabilities are actually provisioned"
 * is validated only as "the referenced profile resolves to a real frozen file", not as a live
 * sandbox/workspace liveness check - no Runner yet actually provisions one per execution.
 */
@Component
public class DeveloperExecutionInputValidator {

	static final String INPUT_SCHEMA_URN = "urn:aiw:schema:developer-execution-input:v1";
	static final String CUSTOMER_PROFILE_TYPE = "customer-profile";
	static final String WEBSITE_REQUIREMENTS_TYPE = "website-requirements";
	static final String DESIGN_PROPOSAL_SET_TYPE = "design-proposal-set";
	static final String AGENT_ID = "developer-agent";
	static final int AGENT_VERSION = 1;
	static final String RULE_ID = "website-developer-integrity";
	static final int RULE_VERSION = 1;
	static final String SKILL_ID = "website-developer";
	static final int SKILL_VERSION = 1;

	private final DeveloperSchemaRegistry schemaRegistry;
	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final AgentDefinitionLoader agentDefinitionLoader;
	private final RuleLoader ruleLoader;
	private final SkillLoader skillLoader;
	private final ObjectMapper objectMapper;

	DeveloperExecutionInputValidator(
			DeveloperSchemaRegistry schemaRegistry,
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			AgentDefinitionLoader agentDefinitionLoader,
			RuleLoader ruleLoader,
			SkillLoader skillLoader,
			ObjectMapper objectMapper) {
		this.schemaRegistry = schemaRegistry;
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.agentDefinitionLoader = agentDefinitionLoader;
		this.ruleLoader = ruleLoader;
		this.skillLoader = skillLoader;
		this.objectMapper = objectMapper;
	}

	public PreExecutionValidationResult validate(UUID projectId, String developerExecutionInputJson) {
		SchemaValidationResult schemaResult = schemaRegistry.validate(INPUT_SCHEMA_URN, developerExecutionInputJson);
		if (!schemaResult.valid()) {
			return new PreExecutionValidationResult(
					schemaResult.issues().stream().map(i -> new PreExecutionValidationIssue(i.path(), i.message())).toList());
		}

		JsonNode input = objectMapper.readTree(developerExecutionInputJson);
		List<PreExecutionValidationIssue> issues = new ArrayList<>();

		validateCanonicalReference(
				projectId,
				input.path("canonicalUpstream").path("customerProfileArtifactVersionRef"),
				input.path("canonicalUpstream").path("customerProfile"),
				"canonicalUpstream/customerProfileArtifactVersionRef",
				"canonicalUpstream/customerProfile",
				CUSTOMER_PROFILE_TYPE,
				issues);
		validateCanonicalReference(
				projectId,
				input.path("canonicalUpstream").path("websiteRequirementsArtifactVersionRef"),
				input.path("canonicalUpstream").path("websiteRequirements"),
				"canonicalUpstream/websiteRequirementsArtifactVersionRef",
				"canonicalUpstream/websiteRequirements",
				WEBSITE_REQUIREMENTS_TYPE,
				issues);
		validateTargetDesign(projectId, input, issues);
		validateAgentCapabilitiesResolvable(issues);

		return new PreExecutionValidationResult(issues);
	}

	private void validateCanonicalReference(
			UUID projectId,
			JsonNode refNode,
			JsonNode embeddedNode,
			String refPath,
			String embeddedPath,
			String expectedType,
			List<PreExecutionValidationIssue> issues) {
		Optional<ArtifactVersion> resolved = resolveArtifactVersion(projectId, refNode, refPath, expectedType, issues);
		if (resolved.isEmpty()) {
			return;
		}

		JsonNode storedContent = objectMapper.readTree(resolved.get().getContent());
		if (!storedContent.equals(embeddedNode)) {
			issues.add(new PreExecutionValidationIssue(
					embeddedPath, "embedded content does not match the exact content of the referenced artifact version"));
		}
	}

	private void validateTargetDesign(UUID projectId, JsonNode input, List<PreExecutionValidationIssue> issues) {
		JsonNode targetDesign = input.path("targetDesign");
		JsonNode refNode = targetDesign.path("designArtifactVersionRef");
		String targetProposalLocalRef = targetDesign.path("targetProposalLocalRef").asString();
		JsonNode embeddedProposal = targetDesign.path("proposal");

		Optional<ArtifactVersion> resolved = resolveArtifactVersion(
				projectId, refNode, "targetDesign/designArtifactVersionRef", DESIGN_PROPOSAL_SET_TYPE, issues);
		if (resolved.isEmpty()) {
			return;
		}

		JsonNode storedProposalSet = objectMapper.readTree(resolved.get().getContent());
		JsonNode matchingProposal = null;
		for (JsonNode proposal : storedProposalSet.path("proposals")) {
			if (targetProposalLocalRef.equals(proposal.path("localRef").asString())) {
				matchingProposal = proposal;
				break;
			}
		}

		if (matchingProposal == null) {
			issues.add(new PreExecutionValidationIssue(
					"targetDesign/targetProposalLocalRef",
					"target proposal '" + targetProposalLocalRef + "' does not exist in the referenced design-proposal-set"));
			return;
		}

		if (!matchingProposal.equals(embeddedProposal)) {
			issues.add(new PreExecutionValidationIssue(
					"targetDesign/proposal",
					"embedded proposal does not match the exact target proposal in the referenced design-proposal-set"));
		}
	}

	private Optional<ArtifactVersion> resolveArtifactVersion(
			UUID projectId, JsonNode refNode, String refPath, String expectedType, List<PreExecutionValidationIssue> issues) {
		String ref = refNode.asString();
		UUID artifactVersionId;
		try {
			artifactVersionId = UUID.fromString(ref);
		} catch (IllegalArgumentException e) {
			issues.add(new PreExecutionValidationIssue(refPath, "not a resolvable artifact version reference: '" + ref + "'"));
			return Optional.empty();
		}

		Optional<ArtifactVersion> versionOpt = artifactVersionRepository.findById(artifactVersionId);
		if (versionOpt.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(refPath, "no artifact version exists for reference '" + ref + "'"));
			return Optional.empty();
		}
		ArtifactVersion version = versionOpt.get();

		Optional<Artifact> artifactOpt = artifactRepository.findById(version.getArtifactId());
		if (artifactOpt.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(refPath, "artifact version '" + ref + "' has no owning artifact"));
			return Optional.empty();
		}
		Artifact artifact = artifactOpt.get();

		if (!artifact.getProjectId().equals(projectId)) {
			issues.add(new PreExecutionValidationIssue(
					refPath, "artifact version '" + ref + "' belongs to a different project - cross-project reference is not allowed"));
			return Optional.empty();
		}
		if (!artifact.getType().equals(expectedType)) {
			issues.add(new PreExecutionValidationIssue(
					refPath, "artifact version '" + ref + "' is of type '" + artifact.getType() + "', expected '" + expectedType + "'"));
			return Optional.empty();
		}

		return Optional.of(version);
	}

	private void validateAgentCapabilitiesResolvable(List<PreExecutionValidationIssue> issues) {
		try {
			agentDefinitionLoader.resolve(AGENT_ID, AGENT_VERSION);
		} catch (RuntimeException e) {
			issues.add(new PreExecutionValidationIssue("agent", AGENT_ID + " v" + AGENT_VERSION + " could not be resolved: " + e.getMessage()));
		}
		try {
			ruleLoader.resolve(RULE_ID, RULE_VERSION);
		} catch (RuntimeException e) {
			issues.add(new PreExecutionValidationIssue("rules", RULE_ID + " v" + RULE_VERSION + " could not be resolved: " + e.getMessage()));
		}
		try {
			skillLoader.resolve(SKILL_ID, SKILL_VERSION);
		} catch (RuntimeException e) {
			issues.add(new PreExecutionValidationIssue("skills", SKILL_ID + " v" + SKILL_VERSION + " could not be resolved: " + e.getMessage()));
		}
	}
}
