package ai.architech.backend.core.validation;

import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Deterministic preflight validation for a Documentation generation request, before any model
 * invocation - implements steps 1 and 2 of the frozen package's own {@code
 * validators/context/PREFLIGHT.md} (lineage: Candidate resolves for real and belongs to the
 * calling project, QA references exactly this Candidate, Candidate's Selected Source Design
 * binding resolves for real; then every profile-required root class actually resolves, and the
 * QA profile/gate combination satisfies the target {@link DocumentationProfile}'s own {@code
 * qaPreconditions}). Steps 3-6 (finding projections, safe facts, {@code authorityCatalog}
 * construction, security redaction, context freezing) are later tickets (AIW-190/191/192), not
 * this one.
 *
 * <p>Same shape as {@link QAExecutionPreflightValidator}: never throws on an invalid input, always
 * returns a typed {@link PreExecutionValidationResult} - matching PREFLIGHT.md's own "typed safe
 * preflight issue, {@code BLOCKED}" framing rather than an uncaught exception.
 */
@Component
public class DocumentationContextPreflightValidator {

	/**
	 * The one QA profile ref that maps to Documentation's own logical {@code qaPreconditions().
	 * qaProfile() == "FULL_RELEASE"} - the counterpart of {@link
	 * QAExecutionPreflightValidator#VALID_QA_PROFILE_REFS}, which additionally allows {@code
	 * website-qa-comparison-readiness@1.0.0} for QA's own, broader purposes. Documentation never
	 * accepts a Comparison-Readiness QA result - both frozen Documentation profiles require {@code
	 * FULL_RELEASE}.
	 */
	static final String EXPECTED_QA_PROFILE_REF = "website-qa-full-release@1.0.0";

	private static final String DESIGN_PROPOSAL_SET_TYPE = "design-proposal-set";

	private static final String CUSTOMER_PROFILE_ROOT = "CUSTOMER_PROFILE";
	private static final String WEBSITE_REQUIREMENTS_ROOT = "WEBSITE_REQUIREMENTS";
	private static final String SELECTED_SOURCE_DESIGN_ROOT = "SELECTED_SOURCE_DESIGN";
	private static final String WEBSITE_IMPLEMENTATION_CANDIDATE_ROOT = "WEBSITE_IMPLEMENTATION_CANDIDATE";
	private static final String QA_RESULT_ROOT = "QA_RESULT";

	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final QaResultRepository qaResultRepository;
	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final ObjectMapper objectMapper;

	DocumentationContextPreflightValidator(
			WebsiteImplementationCandidateRepository candidateRepository,
			QaResultRepository qaResultRepository,
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			ObjectMapper objectMapper) {
		this.candidateRepository = candidateRepository;
		this.qaResultRepository = qaResultRepository;
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.objectMapper = objectMapper;
	}

	public PreExecutionValidationResult validate(
			UUID projectId, UUID candidateId, UUID qaResultId, DocumentationProfile profile) {
		List<PreExecutionValidationIssue> issues = new ArrayList<>();

		Optional<WebsiteImplementationCandidate> candidateOpt = resolveCandidate(projectId, candidateId, issues);
		Optional<QaResult> qaResultOpt =
				candidateOpt.flatMap(candidate -> resolveQaResult(candidate, qaResultId, issues));

		boolean selectedSourceDesignResolved =
				candidateOpt.map(candidate -> validateSelectedSourceDesign(candidate, issues)).orElse(false);

		if (candidateOpt.isPresent()) {
			validateRequiredRoots(projectId, candidateOpt.get(), selectedSourceDesignResolved, qaResultOpt.isPresent(), profile, issues);
		}

		qaResultOpt.ifPresent(qaResult -> {
			validateQaProfilePrecondition(qaResult, issues);
			validateQaGatePrecondition(qaResult, profile, issues);
		});

		return new PreExecutionValidationResult(issues);
	}

	private Optional<WebsiteImplementationCandidate> resolveCandidate(
			UUID projectId, UUID candidateId, List<PreExecutionValidationIssue> issues) {
		Optional<WebsiteImplementationCandidate> candidateOpt = candidateRepository.findById(candidateId);
		if (candidateOpt.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(
					"candidateRef", "no WebsiteImplementationCandidate exists for reference '" + candidateId + "'"));
			return Optional.empty();
		}

		WebsiteImplementationCandidate candidate = candidateOpt.get();
		if (!candidate.getProjectId().equals(projectId)) {
			issues.add(new PreExecutionValidationIssue(
					"candidateRef", "Candidate '" + candidateId + "' does not belong to the calling project"));
			return Optional.empty();
		}

		return Optional.of(candidate);
	}

	private Optional<QaResult> resolveQaResult(
			WebsiteImplementationCandidate candidate, UUID qaResultId, List<PreExecutionValidationIssue> issues) {
		Optional<QaResult> qaResultOpt = qaResultRepository.findById(qaResultId);
		if (qaResultOpt.isEmpty()) {
			issues.add(new PreExecutionValidationIssue("qaResultRef", "no QaResult exists for reference '" + qaResultId + "'"));
			return Optional.empty();
		}

		QaResult qaResult = qaResultOpt.get();
		if (!qaResult.getTestedCandidateId().equals(candidate.getId())) {
			issues.add(new PreExecutionValidationIssue(
					"qaResultRef",
					"QaResult '" + qaResultId + "' was not run against exactly Candidate '" + candidate.getId() + "'"));
			return Optional.empty();
		}

		return Optional.of(qaResult);
	}

	private boolean validateSelectedSourceDesign(
			WebsiteImplementationCandidate candidate, List<PreExecutionValidationIssue> issues) {
		String path = "authoritySnapshot/selectedSourceDesignRef";

		UUID designArtifactVersionId;
		try {
			designArtifactVersionId = UUID.fromString(candidate.getSourceDesignArtifactVersionRef());
		} catch (IllegalArgumentException e) {
			issues.add(new PreExecutionValidationIssue(
					path, "Candidate's own source design artifact version ref is not a resolvable reference: '"
							+ candidate.getSourceDesignArtifactVersionRef() + "'"));
			return false;
		}

		Optional<ArtifactVersion> proposalSetVersionOpt = artifactVersionRepository.findById(designArtifactVersionId);
		if (proposalSetVersionOpt.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(
					path, "Candidate's source design artifact version '" + designArtifactVersionId + "' no longer exists"));
			return false;
		}

		ArtifactVersion proposalSetVersion = proposalSetVersionOpt.get();
		Optional<Artifact> proposalSetArtifact = artifactRepository.findById(proposalSetVersion.getArtifactId());
		if (proposalSetArtifact.isEmpty() || !DESIGN_PROPOSAL_SET_TYPE.equals(proposalSetArtifact.get().getType())) {
			issues.add(new PreExecutionValidationIssue(
					path, "Candidate's source design artifact version '" + designArtifactVersionId
							+ "' is not a '" + DESIGN_PROPOSAL_SET_TYPE + "' artifact"));
			return false;
		}

		JsonNode proposalSetContent = objectMapper.readTree(proposalSetVersion.getContent());
		ArrayNode proposals = (ArrayNode) proposalSetContent.path("proposals");
		boolean proposalExists = false;
		for (JsonNode proposal : proposals) {
			if (candidate.getSourceDesignProposalLocalRef().equals(proposal.path("localRef").asString())) {
				proposalExists = true;
				break;
			}
		}
		if (!proposalExists) {
			issues.add(new PreExecutionValidationIssue(
					path, "Target proposal '" + candidate.getSourceDesignProposalLocalRef()
							+ "' does not exist in design-proposal-set artifact version '" + designArtifactVersionId + "'"));
			return false;
		}

		return true;
	}

	private void validateRequiredRoots(
			UUID projectId,
			WebsiteImplementationCandidate candidate,
			boolean selectedSourceDesignResolved,
			boolean qaResultResolved,
			DocumentationProfile profile,
			List<PreExecutionValidationIssue> issues) {
		for (String requiredRoot : profile.requiredRoots()) {
			boolean resolved =
					switch (requiredRoot) {
						case CUSTOMER_PROFILE_ROOT -> canonicalArtifactExists(projectId, "customer-profile");
						case WEBSITE_REQUIREMENTS_ROOT -> canonicalArtifactExists(projectId, "website-requirements");
						case SELECTED_SOURCE_DESIGN_ROOT -> selectedSourceDesignResolved;
						case WEBSITE_IMPLEMENTATION_CANDIDATE_ROOT -> true; // candidate is already resolved to reach here
						case QA_RESULT_ROOT -> qaResultResolved;
						default -> true; // unknown root classes are not this validator's concern to police
					};
			if (!resolved) {
				issues.add(new PreExecutionValidationIssue(
						"requiredRoots", "Profile '" + profile.ref() + "' requires root '" + requiredRoot
								+ "', which does not resolve for project '" + projectId + "'"));
			}
		}
	}

	private boolean canonicalArtifactExists(UUID projectId, String type) {
		return artifactRepository.findByProjectIdAndType(projectId, type).isPresent();
	}

	private void validateQaProfilePrecondition(QaResult qaResult, List<PreExecutionValidationIssue> issues) {
		if (!EXPECTED_QA_PROFILE_REF.equals(qaResult.getQaProfileRef())) {
			issues.add(new PreExecutionValidationIssue(
					"qaState/qaProfile", "QaResult was evaluated under '" + qaResult.getQaProfileRef()
							+ "', but Documentation generation requires '" + EXPECTED_QA_PROFILE_REF + "'"));
		}
	}

	private void validateQaGatePrecondition(
			QaResult qaResult, DocumentationProfile profile, List<PreExecutionValidationIssue> issues) {
		if (!profile.qaPreconditions().allowedGates().contains(qaResult.getGateOutcome())) {
			issues.add(new PreExecutionValidationIssue(
					"qaState/gate", "QaResult gate '" + qaResult.getGateOutcome() + "' is not one of profile '"
							+ profile.ref() + "'s own allowed gates " + profile.qaPreconditions().allowedGates()));
		}
	}
}
