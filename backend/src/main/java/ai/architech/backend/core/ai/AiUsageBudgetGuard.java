package ai.architech.backend.core.ai;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Prevents a misbehaving agent or execution loop from running up unbounded AI provider cost
 * (AIW-65). Checked once per real attempt, before an {@code AiGateway} call is even made - see
 * {@code AgentRunner#run}, the only caller. Meaningless against the mock provider by
 * construction, not by any special-casing here: {@code AgentExecution.costUsd} is only ever
 * non-null once a real {@link AiProvider} actually reports usage (see {@link CostCalculator}),
 * so a project/agent that has only ever run against the mock provider always sums to zero and
 * never trips either limit.
 *
 * <p>A hard-limit breach throws {@link ApplicationException} directly - not caught or retried
 * by {@code BoundedRetryAgentRunner} (which only catches {@code AgentRunnerException}), so it
 * propagates immediately and definitively instead of being treated as one more transient
 * attempt failure worth retrying. A soft-limit breach only logs a warning and lets the call
 * proceed - both breach kinds are auditable, just at different severities: a hard breach is
 * additionally recorded as the {@code AgentExecution}'s own failure reason by {@code
 * AgentRunner} (the real, queryable audit trail - see {@code RetryBudgetExhaustedException}'s
 * own javadoc for why that convention already exists), a soft breach is a log line.
 */
@Component
public class AiUsageBudgetGuard {

	private static final Logger log = LoggerFactory.getLogger(AiUsageBudgetGuard.class);

	private final AiBudgetProperties properties;
	private final AgentExecutionRepository agentExecutionRepository;

	AiUsageBudgetGuard(AiBudgetProperties properties, AgentExecutionRepository agentExecutionRepository) {
		this.properties = properties;
		this.agentExecutionRepository = agentExecutionRepository;
	}

	public void checkBeforeInvoking(UUID projectId, String agentId) {
		AiBudgetProperties.Limits agentLimits = properties.limitsForAgent(agentId);
		if (agentLimits != null) {
			checkScope("agent '" + agentId + "'", agentExecutionRepository.sumCostUsdByAgentId(agentId), agentLimits);
		}

		AiBudgetProperties.Limits projectLimits = properties.perProject();
		if (projectLimits != null) {
			checkScope(
					"project '" + projectId + "'", agentExecutionRepository.sumCostUsdByProjectId(projectId), projectLimits);
		}
	}

	private static void checkScope(String scopeDescription, BigDecimal spentSoFar, AiBudgetProperties.Limits limits) {
		if (limits.hardLimitUsd() != null && spentSoFar.compareTo(limits.hardLimitUsd()) >= 0) {
			throw new ApplicationException(
					ErrorCode.AI_BUDGET_HARD_LIMIT_EXCEEDED,
					"AI usage hard limit exceeded for " + scopeDescription + ": $" + spentSoFar
							+ " spent so far, hard limit is $" + limits.hardLimitUsd());
		}
		if (limits.softLimitUsd() != null && spentSoFar.compareTo(limits.softLimitUsd()) >= 0) {
			log.warn(
					"AI usage soft limit exceeded for {}: ${} spent so far, soft limit is ${}",
					scopeDescription,
					spentSoFar,
					limits.softLimitUsd());
		}
	}
}
