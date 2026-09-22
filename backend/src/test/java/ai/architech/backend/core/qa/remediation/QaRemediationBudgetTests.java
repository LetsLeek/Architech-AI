package ai.architech.backend.core.qa.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.qa.profiles.QaProfileType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QaRemediationBudgetTests {

	@Test
	void advancesTheCounterWhileBudgetRemains() {
		QaRemediationBudget budget = new QaRemediationBudget(UUID.randomUUID(), "prop-a", QaProfileType.COMPARISON_READINESS);

		assertThat(budget.useRemediationCycle(2)).isTrue();
		assertThat(budget.getRemediationCyclesUsed()).isEqualTo(1);
		assertThat(budget.useRemediationCycle(2)).isTrue();
		assertThat(budget.getRemediationCyclesUsed()).isEqualTo(2);
	}

	@Test
	void refusesAndChangesNothingOnceExhausted() {
		QaRemediationBudget budget = new QaRemediationBudget(UUID.randomUUID(), "prop-a", QaProfileType.COMPARISON_READINESS);
		budget.useRemediationCycle(1);

		boolean secondAttempt = budget.useRemediationCycle(1);

		assertThat(secondAttempt).isFalse();
		assertThat(budget.getRemediationCyclesUsed()).isEqualTo(1);
	}

	@Test
	void hasRemediationCyclesRemainingReflectsCurrentUsage() {
		QaRemediationBudget budget = new QaRemediationBudget(UUID.randomUUID(), "prop-a", QaProfileType.FULL_RELEASE);

		assertThat(budget.hasRemediationCyclesRemaining(1)).isTrue();
		budget.useRemediationCycle(1);
		assertThat(budget.hasRemediationCyclesRemaining(1)).isFalse();
	}

	@Test
	void distinctStagesAreDistinctBudgetsByConstruction() {
		UUID projectId = UUID.randomUUID();
		QaRemediationBudget comparisonBudget = new QaRemediationBudget(projectId, "prop-a", QaProfileType.COMPARISON_READINESS);
		QaRemediationBudget fullReleaseBudget =
				new QaRemediationBudget(projectId, QaRemediationBudget.RELEASE_PATH_LINEAGE_REF, QaProfileType.FULL_RELEASE);

		comparisonBudget.useRemediationCycle(2);

		assertThat(comparisonBudget.getRemediationCyclesUsed()).isEqualTo(1);
		assertThat(fullReleaseBudget.getRemediationCyclesUsed()).isZero();
	}
}
