package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Plain unit tests, no Spring context - the repository is a stub reporting a chosen "spent so far" total directly. */
@ExtendWith(MockitoExtension.class)
class AiUsageBudgetGuardTests {

	private static final String AGENT_ID = "requirements-agent";
	private static final UUID PROJECT_ID = UUID.randomUUID();

	@Mock
	private AgentExecutionRepository agentExecutionRepository;

	@Test
	void allowsInvocationWhenNoLimitsAreConfiguredAtAll() {
		AiBudgetProperties properties = new AiBudgetProperties(Map.of(), null);
		AiUsageBudgetGuard guard = new AiUsageBudgetGuard(properties, agentExecutionRepository);

		assertThatCode(() -> guard.checkBeforeInvoking(PROJECT_ID, AGENT_ID)).doesNotThrowAnyException();
	}

	@Test
	void allowsInvocationWhenSpendIsBelowBothLimits() {
		when(agentExecutionRepository.sumCostUsdByAgentId(AGENT_ID)).thenReturn(new BigDecimal("1.00"));
		when(agentExecutionRepository.sumCostUsdByProjectId(PROJECT_ID)).thenReturn(new BigDecimal("1.00"));
		AiBudgetProperties properties = new AiBudgetProperties(
				Map.of(AGENT_ID, new AiBudgetProperties.Limits(new BigDecimal("2.00"), new BigDecimal("5.00"))),
				new AiBudgetProperties.Limits(new BigDecimal("2.00"), new BigDecimal("5.00")));
		AiUsageBudgetGuard guard = new AiUsageBudgetGuard(properties, agentExecutionRepository);

		assertThatCode(() -> guard.checkBeforeInvoking(PROJECT_ID, AGENT_ID)).doesNotThrowAnyException();
	}

	@Test
	void allowsInvocationButLogsAWarningWhenOnlyTheSoftLimitIsCrossed() {
		when(agentExecutionRepository.sumCostUsdByAgentId(AGENT_ID)).thenReturn(new BigDecimal("3.00"));
		when(agentExecutionRepository.sumCostUsdByProjectId(PROJECT_ID)).thenReturn(new BigDecimal("3.00"));
		AiBudgetProperties properties = new AiBudgetProperties(
				Map.of(AGENT_ID, new AiBudgetProperties.Limits(new BigDecimal("2.00"), new BigDecimal("5.00"))),
				new AiBudgetProperties.Limits(new BigDecimal("2.00"), new BigDecimal("5.00")));
		AiUsageBudgetGuard guard = new AiUsageBudgetGuard(properties, agentExecutionRepository);

		// soft limit exceeded ($3 spent >= $2 soft) but still below hard ($5) - must not block
		assertThatCode(() -> guard.checkBeforeInvoking(PROJECT_ID, AGENT_ID)).doesNotThrowAnyException();
	}

	@Test
	void blocksInvocationWhenTheAgentsHardLimitIsReached() {
		when(agentExecutionRepository.sumCostUsdByAgentId(AGENT_ID)).thenReturn(new BigDecimal("5.00"));
		AiBudgetProperties properties = new AiBudgetProperties(
				Map.of(AGENT_ID, new AiBudgetProperties.Limits(new BigDecimal("2.00"), new BigDecimal("5.00"))), null);
		AiUsageBudgetGuard guard = new AiUsageBudgetGuard(properties, agentExecutionRepository);

		assertThatThrownBy(() -> guard.checkBeforeInvoking(PROJECT_ID, AGENT_ID))
				.isInstanceOf(ApplicationException.class)
				.satisfies(thrown ->
						org.assertj.core.api.Assertions.assertThat(((ApplicationException) thrown).errorCode())
								.isEqualTo(ErrorCode.AI_BUDGET_HARD_LIMIT_EXCEEDED))
				.hasMessageContaining(AGENT_ID);
	}

	@Test
	void blocksInvocationWhenTheProjectsHardLimitIsReachedEvenIfTheAgentItselfIsFine() {
		when(agentExecutionRepository.sumCostUsdByAgentId(AGENT_ID)).thenReturn(BigDecimal.ZERO);
		when(agentExecutionRepository.sumCostUsdByProjectId(PROJECT_ID)).thenReturn(new BigDecimal("5.00"));
		AiBudgetProperties properties = new AiBudgetProperties(
				Map.of(AGENT_ID, new AiBudgetProperties.Limits(new BigDecimal("2.00"), new BigDecimal("5.00"))),
				new AiBudgetProperties.Limits(new BigDecimal("2.00"), new BigDecimal("5.00")));
		AiUsageBudgetGuard guard = new AiUsageBudgetGuard(properties, agentExecutionRepository);

		assertThatThrownBy(() -> guard.checkBeforeInvoking(PROJECT_ID, AGENT_ID))
				.isInstanceOf(ApplicationException.class)
				.hasMessageContaining(PROJECT_ID.toString());
	}

	@Test
	void aScopeWithOnlyASoftLimitConfiguredNeverBlocks() {
		when(agentExecutionRepository.sumCostUsdByAgentId(AGENT_ID)).thenReturn(new BigDecimal("1000.00"));
		AiBudgetProperties properties =
				new AiBudgetProperties(Map.of(AGENT_ID, new AiBudgetProperties.Limits(new BigDecimal("2.00"), null)), null);
		AiUsageBudgetGuard guard = new AiUsageBudgetGuard(properties, agentExecutionRepository);

		assertThatCode(() -> guard.checkBeforeInvoking(PROJECT_ID, AGENT_ID)).doesNotThrowAnyException();
	}
}
