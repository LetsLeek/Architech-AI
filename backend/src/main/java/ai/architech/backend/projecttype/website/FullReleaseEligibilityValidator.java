package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@code core/VALIDATORS.md}'s own {@code FullReleaseEligibilityValidator}: "Requires a valid,
 * non-stale FULL_RELEASE PASS for the exact active release-path Candidate before transition to
 * Final Human/Customer Approval" (AIW-178). Simpler than {@link ComparisonReadinessBarrierEvaluator}
 * (single Candidate, not three Variant Lineages) and takes the same stateless, always-fresh
 * shape: eligibility is decided by whether the *exact* {@code releasePathCandidateId} passed in
 * has at least one matching {@code PASS} {@code QaResult} for the requested {@code qaProfileRef}.
 *
 * <p>Which Candidate currently *is* the release path is deliberately not this validator's concern
 * - "Full Release runs only against the exact selected or otherwise authorized release-path
 * Candidate" describes an input contract, not something QA itself discovers or decides (QA does
 * not select). A caller that later re-points the release path at a remediated Candidate (a
 * distinct, new immutable id - Candidates are never mutated, AIW-145) automatically makes any
 * prior {@code PASS} against the old id inapplicable to the new one - "becomes stale after any
 * source mutation" holds the same way {@link ComparisonReadinessBarrierEvaluator}'s own staleness
 * guarantee does, by exact-id matching rather than a separate staleness check.
 *
 * <p><b>Requirement-by-requirement Full Release coverage</b> ({@code RequirementCoverageValidator}
 * in {@code core/VALIDATORS.md}) and <b>Domain Result construction</b> ({@code
 * DomainResultInvariantValidator}) are both named as their own, separate, not-yet-ticketed
 * validators - the same deferral {@link ai.architech.backend.core.qa.policy.QAPolicyAggregator}
 * already documents for AIW-176. Every other Full Release domain-coverage requirement this
 * ticket's own AC names (all required V1 Domains evaluated with Integration/Localization
 * conditional applicability; SEO/Metadata and Performance baselines included; explicit finding
 * code overrides for placeholder leakage and required metadata/indexability) is already real,
 * already-built infrastructure this validator itself does not need to duplicate: the frozen
 * {@code website-qa-full-release@1.0.0} profile (AIW-175) already declares those domains required
 * with their own checks (AIW-172), and {@code QAPolicyAggregator} (AIW-176) already enforces the
 * explicit code overrides the profile names.
 */
@Component
public class FullReleaseEligibilityValidator {

	private static final String PASS_GATE_OUTCOME = "PASS";

	private final QaResultRepository qaResultRepository;

	FullReleaseEligibilityValidator(QaResultRepository qaResultRepository) {
		this.qaResultRepository = qaResultRepository;
	}

	public FullReleaseEligibility validate(UUID releasePathCandidateId, String qaProfileRef) {
		List<QaResult> results = qaResultRepository.findByTestedCandidateIdOrderByCreatedAtAsc(releasePathCandidateId);
		boolean eligible = results.stream()
				.anyMatch(result -> result.getTestedCandidateId().equals(releasePathCandidateId)
						&& result.getQaProfileRef().equals(qaProfileRef)
						&& PASS_GATE_OUTCOME.equals(result.getGateOutcome()));
		return new FullReleaseEligibility(releasePathCandidateId, eligible);
	}
}
