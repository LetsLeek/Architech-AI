package ai.architech.backend.core.documentation.triggers;

import ai.architech.backend.projecttype.website.FullReleaseEligibility;
import ai.architech.backend.projecttype.website.FullReleaseEligibilityValidator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides which Documentation profile (if any) an automatic {@code
 * documentation-workflow-policy.yaml} trigger event resolves to (AIW-202) - the {@code
 * enabledTriggers} half of that policy. {@code onDemand} generation needs no equivalent class:
 * {@link ai.architech.backend.core.validation.DocumentationContextPreflightValidator} already
 * checks exactly the on-demand conditions table (per profile, {@code qaResult.getGateOutcome()}
 * against {@code profile.qaPreconditions().allowedGates()} - {@code CUSTOMER_HANDOVER} allows only
 * {@code PASS}, {@code TECHNICAL_HANDOVER} allows {@code PASS} or {@code HOLD}), so an on-demand
 * caller simply invokes {@code DocumentationGenerationOrchestrator} directly for its chosen
 * profile and lets that existing preflight gate decide - duplicating that logic here would only
 * risk the two checks drifting apart.
 *
 * <p><b>{@code FULL_RELEASE_QA_FINALIZED}</b>: narrower than on-demand Technical - requires gate
 * {@code PASS} specifically, not {@code HOLD}. Delegates to {@link FullReleaseEligibilityValidator}
 * (AIW-178), the exact existing component for "is the given exact release-path Candidate currently
 * {@code FULL_RELEASE PASS}-eligible" - "release-relevant exact Candidate" in the trigger policy
 * means precisely what that validator's own javadoc already states: the caller supplies the
 * release-path candidate id, this class does not discover it.
 *
 * <p><b>{@code SCOPED_APPROVAL_RECORDED}</b>: a real, honest gap, not silently worked around - no
 * {@code ApprovalRecord} type exists anywhere in this codebase (confirmed via {@code grep -rl
 * "ApprovalRecord" backend/src/main/java} returning empty), so nothing here can independently
 * verify "affirmative approval of exact QA-eligible Candidate" the way {@link
 * FullReleaseEligibilityValidator} verifies QA eligibility. This method therefore requires the
 * caller to assert {@code externallyConfirmedApproval} explicitly, the same "accepts caller-
 * supplied signals rather than computing them itself" boundary {@code QAPolicyAggregator}'s own
 * javadoc already establishes for {@code requiredCoverageComplete}/{@code
 * materiallyUnfulfilledMustRequirementExists} - it still independently re-verifies the policy's own
 * second condition ("bound {@code FULL_RELEASE} QA {@code PASS}") via the same eligibility
 * validator, so a caller cannot bypass that half by simply asserting {@code true}.
 *
 * <p><b>{@code DEPLOYMENT_RECORDED}</b>: the one {@code optionalRefreshEvents} entry - also
 * presupposes a {@code DeploymentRecord} type that does not exist anywhere in this codebase. Unlike
 * the two profile-selecting events above, this one is a *refresh* signal for whichever Documentation
 * Lines already exist for a project, not a fresh profile selection - see {@link
 * #refreshGenerationReason} and {@link DocumentationTriggerService#dispatchDeploymentRefresh}.
 */
@Component
public class DocumentationWorkflowTriggerEvaluator {

	/** Matches {@code DocumentationContextPreflightValidator.EXPECTED_QA_PROFILE_REF} - both frozen Documentation profiles require this. */
	static final String FULL_RELEASE_QA_PROFILE_REF = "website-qa-full-release@1.0.0";

	static final String TECHNICAL_HANDOVER_PROFILE_REF = "TECHNICAL_HANDOVER@1.0.0";
	static final String CUSTOMER_HANDOVER_PROFILE_REF = "CUSTOMER_HANDOVER@1.0.0";

	private final FullReleaseEligibilityValidator fullReleaseEligibilityValidator;

	DocumentationWorkflowTriggerEvaluator(FullReleaseEligibilityValidator fullReleaseEligibilityValidator) {
		this.fullReleaseEligibilityValidator = fullReleaseEligibilityValidator;
	}

	/**
	 * Resolves {@code FULL_RELEASE_QA_FINALIZED}/{@code SCOPED_APPROVAL_RECORDED} to the profile ref
	 * they should trigger, or {@link Optional#empty()} when the event's own conditions aren't met.
	 * {@code externallyConfirmedApproval} is only consulted for {@code SCOPED_APPROVAL_RECORDED} -
	 * ignored otherwise. Throws {@link IllegalArgumentException} for {@code DEPLOYMENT_RECORDED},
	 * which is not a profile-selecting event - use {@link #refreshGenerationReason} for that one.
	 */
	public Optional<String> resolveProfileRef(
			DocumentationTriggerEvent event, UUID releasePathCandidateId, boolean externallyConfirmedApproval) {
		FullReleaseEligibility eligibility = fullReleaseEligibilityValidator.validate(releasePathCandidateId, FULL_RELEASE_QA_PROFILE_REF);

		return switch (event) {
			case FULL_RELEASE_QA_FINALIZED -> eligibility.eligible() ? Optional.of(TECHNICAL_HANDOVER_PROFILE_REF) : Optional.empty();
			case SCOPED_APPROVAL_RECORDED ->
					(externallyConfirmedApproval && eligibility.eligible()) ? Optional.of(CUSTOMER_HANDOVER_PROFILE_REF) : Optional.empty();
			case DEPLOYMENT_RECORDED -> throw new IllegalArgumentException(
					"DEPLOYMENT_RECORDED does not select a profile - use refreshGenerationReason for the refresh path");
		};
	}

	/**
	 * {@code DEPLOYMENT_RECORDED}'s own generation reason ({@code documentation-package-version.
	 * schema.json}'s {@code generationReasons} enum) - always {@code AUTHORITY_STATE_CHANGE}, since a
	 * deployment is new authority state that may make an already-published package stale. There is no
	 * eligibility condition to check here beyond "a canonical line already exists for this profile" -
	 * {@link DocumentationTriggerService#dispatchDeploymentRefresh} checks that directly.
	 */
	public String refreshGenerationReason(DocumentationTriggerEvent event) {
		if (event != DocumentationTriggerEvent.DEPLOYMENT_RECORDED) {
			throw new IllegalArgumentException("refreshGenerationReason is only meaningful for DEPLOYMENT_RECORDED");
		}
		return "AUTHORITY_STATE_CHANGE";
	}
}
