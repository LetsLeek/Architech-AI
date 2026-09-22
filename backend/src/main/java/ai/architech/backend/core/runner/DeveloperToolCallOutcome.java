package ai.architech.backend.core.runner;

import ai.architech.backend.core.toolexecution.ToolCapability;
import ai.architech.backend.core.toolexecution.ToolExecutionStatus;

/** The result of dispatching exactly one requested tool call (AIW-184) - {@code detail} is what the caller both persists as {@code ToolExecution.rawDiagnostics} and feeds back as the next turn's {@code tool_result} content. */
record DeveloperToolCallOutcome(ToolExecutionStatus status, ToolCapability capability, String detail) {

	static DeveloperToolCallOutcome succeeded(ToolCapability capability, String detail) {
		return new DeveloperToolCallOutcome(ToolExecutionStatus.SUCCEEDED, capability, detail);
	}

	static DeveloperToolCallOutcome denied(ToolCapability capability, String detail) {
		return new DeveloperToolCallOutcome(ToolExecutionStatus.DENIED, capability, detail);
	}

	static DeveloperToolCallOutcome failed(ToolCapability capability, String detail) {
		return new DeveloperToolCallOutcome(ToolExecutionStatus.FAILED, capability, detail);
	}

	static DeveloperToolCallOutcome errored(ToolCapability capability, String detail) {
		return new DeveloperToolCallOutcome(ToolExecutionStatus.ERROR, capability, detail);
	}

	boolean isError() {
		return status != ToolExecutionStatus.SUCCEEDED;
	}
}
