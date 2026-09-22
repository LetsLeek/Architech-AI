package ai.architech.backend.core.qa.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.qa.policy.HoldReason;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QaEscalationRouterTests {

	private final QaEscalationRouter router = new QaEscalationRouter();

	@Test
	void authorityIssuesRouteToAuthorityResolutionNeverDeveloperRemediation() {
		EscalationDecision decision = router.route(Set.of(HoldReason.AUTHORITY_RESOLUTION_REQUIRED), true, false);

		assertThat(decision.route()).isEqualTo(EscalationRoute.AUTHORITY_RESOLUTION);
	}

	@Test
	void authorityIssuesTakePrecedenceOverABlockingFindingAlsoPresent() {
		EscalationDecision decision =
				router.route(EnumSet.of(HoldReason.AUTHORITY_RESOLUTION_REQUIRED, HoldReason.BLOCKING_CANDIDATE_FINDING), true, false);

		assertThat(decision.route()).isEqualTo(EscalationRoute.AUTHORITY_RESOLUTION);
	}

	@Test
	void evaluationIncompleteRoutesToQaPlatformRetryNotDeveloper() {
		EscalationDecision decision = router.route(Set.of(HoldReason.EVALUATION_INCOMPLETE), true, false);

		assertThat(decision.route()).isEqualTo(EscalationRoute.QA_PLATFORM_RETRY);
	}

	@Test
	void executionInvalidRoutesToQaPlatformRetryNotDeveloper() {
		EscalationDecision decision = router.route(Set.of(HoldReason.EXECUTION_INVALID), true, false);

		assertThat(decision.route()).isEqualTo(EscalationRoute.QA_PLATFORM_RETRY);
	}

	@Test
	void aBlockingFindingWithBudgetAvailableRoutesToDeveloperRemediation() {
		EscalationDecision decision = router.route(Set.of(HoldReason.BLOCKING_CANDIDATE_FINDING), true, false);

		assertThat(decision.route()).isEqualTo(EscalationRoute.DEVELOPER_REMEDIATION);
		assertThat(decision.reasonCode()).isEqualTo("BLOCKING_CANDIDATE_FINDING");
	}

	@Test
	void anExhaustedBudgetRoutesToHumanEscalationNeverFabricatesPass() {
		EscalationDecision decision = router.route(Set.of(HoldReason.BLOCKING_CANDIDATE_FINDING), false, false);

		assertThat(decision.route()).isEqualTo(EscalationRoute.HUMAN_ESCALATION);
		assertThat(decision.reasonCode()).isEqualTo("REMEDIATION_BUDGET_EXHAUSTED");
	}

	@Test
	void aRepeatedEquivalentBlockingFindingTriggersEarlyHumanEscalationEvenWithBudgetRemaining() {
		EscalationDecision decision = router.route(Set.of(HoldReason.BLOCKING_CANDIDATE_FINDING), true, true);

		assertThat(decision.route()).isEqualTo(EscalationRoute.HUMAN_ESCALATION);
		assertThat(decision.reasonCode()).isEqualTo("REPEATED_BLOCKING_FINDING");
	}

	@Test
	void humanReviewRequiredWithBudgetAvailableRoutesToDeveloperRemediation() {
		EscalationDecision decision = router.route(Set.of(HoldReason.HUMAN_REVIEW_REQUIRED), true, false);

		assertThat(decision.route()).isEqualTo(EscalationRoute.DEVELOPER_REMEDIATION);
	}

	@Test
	void aNonRoutableHoldReasonSetIsRejected() {
		assertThatThrownBy(() -> router.route(Set.of(), true, false)).isInstanceOf(IllegalArgumentException.class);
	}
}
