package ai.architech.backend.core.sandbox;

/**
 * Matches {@code tool-capability-profile.v1.yaml}'s {@code projectExecution.approvedTasks}
 * exactly. Each maps to exactly one fixed command in {@link ProjectExecutionCapability} - never a
 * caller-supplied command string, which is what {@code unrestrictedShell: false} means
 * structurally in this codebase.
 */
public enum ProjectExecutionTask {
	INSTALL,
	TYPECHECK,
	LINT,
	TEST,
	BUILD,
	LOCAL_RUNTIME
}
