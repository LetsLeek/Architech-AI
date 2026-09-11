package ai.architech.backend.core.runner;

/** Wraps any failure during a run() attempt. The failing AgentExecution (see its failureReason) is the audit trail, not this exception's message alone. */
public class AgentRunnerException extends RuntimeException {

	public AgentRunnerException(String message, Throwable cause) {
		super(message, cause);
	}
}
