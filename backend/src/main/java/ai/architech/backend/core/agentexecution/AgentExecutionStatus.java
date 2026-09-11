package ai.architech.backend.core.agentexecution;

/**
 * A successful model response alone never means SUCCEEDED - that requires the candidate to
 * also pass all required validation and be atomically persisted as canonical. Until then an
 * execution stays RUNNING or moves to FAILED; nothing in between is treated as success.
 */
public enum AgentExecutionStatus {
	PENDING,
	RUNNING,
	SUCCEEDED,
	FAILED
}
