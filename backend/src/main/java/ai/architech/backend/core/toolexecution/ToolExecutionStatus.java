package ai.architech.backend.core.toolexecution;

/**
 * The four outcomes AIW-148's acceptance criteria name explicitly: a call that ran and
 * succeeded, one the dispatch layer refused before any process started (not on the
 * tool-capability-profile allowlist), one that ran but the tool/source itself reported failure
 * (e.g. a nonzero exit code), and one where the sandbox/process infrastructure itself broke
 * (timeout, IO failure) rather than the tool reporting anything meaningful.
 */
public enum ToolExecutionStatus {
	SUCCEEDED,
	DENIED,
	FAILED,
	ERROR
}
