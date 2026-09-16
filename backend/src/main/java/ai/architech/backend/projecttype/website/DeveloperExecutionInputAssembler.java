package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AIW-151's trusted model-context assembly layer: materializes the exact validated Customer
 * Profile, Website Requirements, one target Design Proposal, and the frozen V1 technical/
 * execution context into a {@code developer-execution-input.v1} payload - the single input
 * artifact {@link ai.architech.backend.core.agent.AgentDefinitionLoader} resolves for
 * {@code developer-agent} (AIW-132's own agent.yaml declares exactly one input artifact type,
 * so this payload is fed straight into the already-generic
 * {@link ai.architech.backend.core.runner.AgentRunner#runWithInputArtifacts} - no new Runner
 * mechanics were needed for Developer's single-composed-input shape).
 *
 * <p>Deterministic and AI-free by construction: every canonical field is a direct embed of
 * already-persisted, already-validated content, never an AI-generated summary - "preserve
 * unknowns, conflicts, requirement strengths and opaque references" is structural here, not a
 * behavior this class has to remember to uphold. Excludes sibling proposals by construction too:
 * exactly one proposal (the one matching {@code targetProposalLocalRef}) is ever embedded, never
 * the other two in the same design-proposal-set.
 *
 * <p>{@code runtimeProfileRef}/{@code dependencyPolicyRef}/{@code verificationPolicyRef}/
 * {@code toolCapabilityProfileRef}/{@code skillProfileRef} are the frozen V1 profiles' own
 * stable ids (there is only one version of each in V1) - {@code developmentBaseRef} is the one
 * technical-context value that is genuinely per-project (AIW-153's own
 * {@code DevelopmentBaseRef}), so it is a caller-supplied parameter rather than a constant here.
 * {@code integrationContext.integrationContracts} is always empty in V1: AIW-144's Developer-safe
 * Integration Contract projection does not exist yet (still AIW-133's placeholder schema).
 */
@Component
public class DeveloperExecutionInputAssembler {

	static final String CUSTOMER_PROFILE_TYPE = "customer-profile";
	static final String WEBSITE_REQUIREMENTS_TYPE = "website-requirements";
	static final String DESIGN_PROPOSAL_SET_TYPE = "design-proposal-set";

	static final String RUNTIME_PROFILE_REF = "website-react-typescript-vite-client-v1";
	static final String DEPENDENCY_POLICY_REF = "website-developer-dependency-policy-v1";
	static final String VERIFICATION_POLICY_REF = "website-developer-verification-policy-v1";
	static final String TOOL_CAPABILITY_PROFILE_REF = "website-developer-tools-v1";
	static final String SKILL_PROFILE_REF = "website-developer-skill-profile-v1";
	static final String AGENT_CONTRACT_VERSION = "1.0.0";
	static final String RULESET_VERSION = "1.0.0";

	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final ObjectMapper objectMapper;

	DeveloperExecutionInputAssembler(
			ArtifactRepository artifactRepository, ArtifactVersionRepository artifactVersionRepository, ObjectMapper objectMapper) {
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.objectMapper = objectMapper;
	}

	public String assemble(UUID projectId, String targetProposalLocalRef, String developmentBaseRef, int maxCorrectionCycles) {
		return assemble(projectId, targetProposalLocalRef, developmentBaseRef, maxCorrectionCycles, null);
	}

	/**
	 * AIW-180's {@code QA_REMEDIATION} shape - the one meaningful difference from {@link #assemble}
	 * is {@code targetDesign}/{@code technicalContext}: those come from {@code sourceCandidate}'s
	 * own exact {@code sourceDesignArtifactVersionRef}/{@code sourceDesignProposalLocalRef}/{@code
	 * runtimeProfileRef} - never "latest" and never another variant - satisfying "Start from the
	 * exact source Candidate state" and "Product Authority, Source Design and Variant Lineage
	 * remain stable for normal remediation." {@code canonicalUpstream} (Customer Profile/Website
	 * Requirements) still resolves to the project's current canonical artifacts, same as {@link
	 * #assemble} - {@code WebsiteImplementationCandidate} does not itself track which exact
	 * upstream artifact versions its own originating execution used (no such field exists,
	 * AIW-145), so full byte-exact upstream stability across a remediation cycle is not yet
	 * something this codebase can verify; a real gap, not silently worked around.
	 */
	public String assembleForRemediation(
			UUID projectId,
			WebsiteImplementationCandidate sourceCandidate,
			String sourceQaResultRef,
			List<String> authorizedFindingRefs,
			List<String> relevantEvidenceRefs,
			String developmentBaseRef,
			int maxCorrectionCycles) {
		ArtifactVersion customerProfile = latestVersion(projectId, CUSTOMER_PROFILE_TYPE);
		ArtifactVersion websiteRequirements = latestVersion(projectId, WEBSITE_REQUIREMENTS_TYPE);

		UUID designArtifactVersionId = UUID.fromString(sourceCandidate.getSourceDesignArtifactVersionRef());
		ArtifactVersion proposalSet = artifactVersionRepository
				.findById(designArtifactVersionId)
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.CANONICAL_ARTIFACT_NOT_FOUND,
						"Source Candidate's own design artifact version " + designArtifactVersionId + " no longer exists"));
		JsonNode proposalSetContent = objectMapper.readTree(proposalSet.getContent());
		JsonNode targetProposal =
				findProposal(proposalSetContent, sourceCandidate.getSourceDesignProposalLocalRef(), proposalSet.getArtifactId());

		ObjectNode root = objectMapper.createObjectNode();
		root.set("projectContext", remediationProjectContext(projectId));
		root.set("canonicalUpstream", canonicalUpstream(customerProfile, websiteRequirements));
		root.set("targetDesign", targetDesign(proposalSet, sourceCandidate.getSourceDesignProposalLocalRef(), targetProposal));
		root.set("technicalContext", technicalContextFromCandidate(sourceCandidate, developmentBaseRef));
		root.set("integrationContext", integrationContext());
		root.set("executionContext", executionContext(maxCorrectionCycles, null));
		root.set(
				"remediationContext",
				remediationContext(sourceCandidate, sourceQaResultRef, authorizedFindingRefs, relevantEvidenceRefs));

		return objectMapper.writeValueAsString(root);
	}

	public String assemble(
			UUID projectId,
			String targetProposalLocalRef,
			String developmentBaseRef,
			int maxCorrectionCycles,
			RetryContext retryContext) {
		ArtifactVersion customerProfile = latestVersion(projectId, CUSTOMER_PROFILE_TYPE);
		ArtifactVersion websiteRequirements = latestVersion(projectId, WEBSITE_REQUIREMENTS_TYPE);
		ArtifactVersion proposalSet = latestVersion(projectId, DESIGN_PROPOSAL_SET_TYPE);

		JsonNode proposalSetContent = objectMapper.readTree(proposalSet.getContent());
		JsonNode targetProposal = findProposal(proposalSetContent, targetProposalLocalRef, proposalSet.getArtifactId());

		ObjectNode root = objectMapper.createObjectNode();
		root.set("projectContext", projectContext(projectId));
		root.set("canonicalUpstream", canonicalUpstream(customerProfile, websiteRequirements));
		root.set("targetDesign", targetDesign(proposalSet, targetProposalLocalRef, targetProposal));
		root.set("technicalContext", technicalContext(developmentBaseRef));
		root.set("integrationContext", integrationContext());
		root.set("executionContext", executionContext(maxCorrectionCycles, retryContext));

		return objectMapper.writeValueAsString(root);
	}

	private ObjectNode projectContext(UUID projectId) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("projectRef", projectId.toString());
		node.put("projectType", "WEBSITE");
		node.put("operation", "INITIAL_GENERATION");
		return node;
	}

	private ObjectNode remediationProjectContext(UUID projectId) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("projectRef", projectId.toString());
		node.put("projectType", "WEBSITE");
		node.put("operation", "QA_REMEDIATION");
		return node;
	}

	private ObjectNode technicalContextFromCandidate(WebsiteImplementationCandidate sourceCandidate, String developmentBaseRef) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("runtimeProfileRef", sourceCandidate.getRuntimeProfileRef());
		node.put("developmentBaseRef", developmentBaseRef);
		node.put("dependencyPolicyRef", DEPENDENCY_POLICY_REF);
		node.put("verificationPolicyRef", VERIFICATION_POLICY_REF);
		node.put("toolCapabilityProfileRef", TOOL_CAPABILITY_PROFILE_REF);
		return node;
	}

	private ObjectNode remediationContext(
			WebsiteImplementationCandidate sourceCandidate,
			String sourceQaResultRef,
			List<String> authorizedFindingRefs,
			List<String> relevantEvidenceRefs) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("sourceCandidateRef", sourceCandidate.getId().toString());
		node.put("sourceRepositoryStateRef", sourceCandidate.getRepositoryStateRef());
		node.put("sourceQAResultRef", sourceQaResultRef);
		ArrayNode findingRefs = objectMapper.createArrayNode();
		authorizedFindingRefs.forEach(findingRefs::add);
		node.set("authorizedFindingRefs", findingRefs);
		ArrayNode evidenceRefs = objectMapper.createArrayNode();
		if (relevantEvidenceRefs != null) {
			relevantEvidenceRefs.forEach(evidenceRefs::add);
		}
		node.set("relevantEvidenceRefs", evidenceRefs);
		return node;
	}

	private ObjectNode canonicalUpstream(ArtifactVersion customerProfile, ArtifactVersion websiteRequirements) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("customerProfileArtifactVersionRef", customerProfile.getId().toString());
		node.set("customerProfile", objectMapper.readTree(customerProfile.getContent()));
		node.put("websiteRequirementsArtifactVersionRef", websiteRequirements.getId().toString());
		node.set("websiteRequirements", objectMapper.readTree(websiteRequirements.getContent()));
		return node;
	}

	private ObjectNode targetDesign(ArtifactVersion proposalSet, String targetProposalLocalRef, JsonNode targetProposal) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("designArtifactVersionRef", proposalSet.getId().toString());
		node.put("targetProposalLocalRef", targetProposalLocalRef);
		node.set("proposal", targetProposal);
		return node;
	}

	private ObjectNode technicalContext(String developmentBaseRef) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("runtimeProfileRef", RUNTIME_PROFILE_REF);
		node.put("developmentBaseRef", developmentBaseRef);
		node.put("dependencyPolicyRef", DEPENDENCY_POLICY_REF);
		node.put("verificationPolicyRef", VERIFICATION_POLICY_REF);
		node.put("toolCapabilityProfileRef", TOOL_CAPABILITY_PROFILE_REF);
		return node;
	}

	private ObjectNode integrationContext() {
		ObjectNode node = objectMapper.createObjectNode();
		node.set("integrationContracts", objectMapper.createArrayNode());
		return node;
	}

	private ObjectNode executionContext(int maxCorrectionCycles, RetryContext retryContext) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("agentContractVersion", AGENT_CONTRACT_VERSION);
		node.put("rulesetVersion", RULESET_VERSION);
		node.put("skillProfileRef", SKILL_PROFILE_REF);

		ObjectNode correctionBudget = objectMapper.createObjectNode();
		correctionBudget.put("maxCorrectionCycles", maxCorrectionCycles);
		node.set("correctionBudget", correctionBudget);

		if (retryContext != null) {
			ObjectNode retryNode = objectMapper.createObjectNode();
			retryNode.put("priorAgentExecutionRef", retryContext.priorAgentExecutionRef());
			retryNode.put("retryReasonCode", retryContext.retryReasonCode());
			retryNode.put("failureEvidenceSummary", retryContext.failureEvidenceSummary());
			node.set("retryContext", retryNode);
		}

		return node;
	}

	private JsonNode findProposal(JsonNode proposalSetContent, String targetProposalLocalRef, UUID proposalSetArtifactId) {
		ArrayNode proposals = (ArrayNode) proposalSetContent.path("proposals");
		for (JsonNode proposal : proposals) {
			if (targetProposalLocalRef.equals(proposal.path("localRef").asString())) {
				return proposal;
			}
		}
		throw new IllegalArgumentException(
				"Target proposal '" + targetProposalLocalRef + "' does not exist in design-proposal-set artifact "
						+ proposalSetArtifactId);
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
