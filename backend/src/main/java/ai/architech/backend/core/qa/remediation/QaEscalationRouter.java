package ai.architech.backend.core.qa.remediation;

import ai.architech.backend.core.qa.policy.HoldReason;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministically decides where a {@code HOLD} {@code QaResult}'s own resolution
 * responsibility routes to (AIW-181), given {@link ai.architech.backend.core.qa.policy.QAPolicyAggregator}'s
 * already-computed {@link HoldReason} set plus two Workflow-owned signals this router does not
 * compute itself: whether Developer remediation budget remains ({@link
 * QaRemediationBudget#hasRemediationCyclesRemaining}) and whether a repeated/equivalent blocking
 * Finding was just detected across remediation cycles (the same exact-fingerprint-match idiom
 * {@code core.qa.invariants.FindingDeduplicationValidator} already established, applied across
 * cycles instead of within one batch).
 *
 * <p>Precedence mirrors the AC's own ordering: an Authority issue always routes to Authority
 * resolution, never Developer remediation, regardless of remaining budget - "Authority issues
 * route to authority-resolution owners, not Developer remediation." An incomplete/invalid
 * evaluation routes to QA/platform retry, never Developer, by the same "not Developer by default"
 * rule. Only once neither applies does a blocking Finding actually consume remediation budget -
 * and a repeated equivalent blocking Finding or an already-exhausted budget both route straight
 * to Human escalation, never to a fabricated PASS or a silently retried Developer cycle.
 */
@Component
public class QaEscalationRouter {

	public EscalationDecision route(Set<HoldReason> holdReasons, boolean remediationBudgetAvailable, boolean repeatedBlockingFindingDetected) {
		if (holdReasons.contains(HoldReason.AUTHORITY_RESOLUTION_REQUIRED)) {
			return new EscalationDecision(EscalationRoute.AUTHORITY_RESOLUTION, "AUTHORITY_RESOLUTION_REQUIRED");
		}
		if (holdReasons.contains(HoldReason.EVALUATION_INCOMPLETE) || holdReasons.contains(HoldReason.EXECUTION_INVALID)) {
			return new EscalationDecision(EscalationRoute.QA_PLATFORM_RETRY, "EVALUATION_OR_INFRASTRUCTURE_ISSUE");
		}
		if (holdReasons.contains(HoldReason.BLOCKING_CANDIDATE_FINDING) || holdReasons.contains(HoldReason.HUMAN_REVIEW_REQUIRED)) {
			if (repeatedBlockingFindingDetected) {
				return new EscalationDecision(EscalationRoute.HUMAN_ESCALATION, "REPEATED_BLOCKING_FINDING");
			}
			if (!remediationBudgetAvailable) {
				return new EscalationDecision(EscalationRoute.HUMAN_ESCALATION, "REMEDIATION_BUDGET_EXHAUSTED");
			}
			return new EscalationDecision(EscalationRoute.DEVELOPER_REMEDIATION, "BLOCKING_CANDIDATE_FINDING");
		}
		throw new IllegalArgumentException("No routable hold reason present - this QaResult was not actually a HOLD: " + holdReasons);
	}
}
