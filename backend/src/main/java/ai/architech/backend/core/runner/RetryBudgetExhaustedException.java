package ai.architech.backend.core.runner;

/** Thrown when every attempt within the platform-bounded retry budget has failed. The chain of FAILED AgentExecution rows is the real audit trail, not this exception. */
public class RetryBudgetExhaustedException extends RuntimeException {

	public RetryBudgetExhaustedException(String agentId, int maxAttempts, Throwable lastFailure) {
		super("Agent '" + agentId + "' failed on all " + maxAttempts + " permitted attempts", lastFailure);
	}
}
