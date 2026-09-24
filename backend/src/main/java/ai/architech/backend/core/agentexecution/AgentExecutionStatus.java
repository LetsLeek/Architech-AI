package ai.architech.backend.core.agentexecution;

/**
 * A successful model response alone never means SUCCEEDED - that requires the candidate to
 * also pass all required validation and be atomically persisted as canonical. Until then an
 * execution stays RUNNING or moves to FAILED; nothing in between is treated as success.
 *
 * <p>{@code BLOCKED} and {@code ERROR} (AIW-149) extend this beyond Requirements/Designer's own
 * two-outcome model for the Website Developer Agent's frozen {@code SUCCESS | BLOCKED | FAILED |
 * ERROR} semantics (no {@code PARTIAL_SUCCESS}): a valid semantic {@code BLOCKED} agent result
 * (a genuine nonlocal completion blocker the agent cannot resolve through further authorized
 * coding) is not the same failure as a Developer-owned defect or exhausted correction budget
 * ({@code FAILED}), and neither is the same as a Runner/sandbox/Core infrastructure malfunction
 * ({@code ERROR}) that must never trigger speculative source edits. Both new values are additive
 * for every other agent - nothing here changes what Requirements/Designer executions can reach.
 */
public enum AgentExecutionStatus {
	PENDING,
	RUNNING,
	SUCCEEDED,
	BLOCKED,
	FAILED,
	ERROR
}
