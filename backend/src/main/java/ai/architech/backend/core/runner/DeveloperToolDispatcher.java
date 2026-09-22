package ai.architech.backend.core.runner;

import ai.architech.backend.core.developer.tooling.DeveloperToolCapabilityProfile;
import ai.architech.backend.core.sandbox.FilesystemCapability;
import ai.architech.backend.core.sandbox.GitInspectionCapability;
import ai.architech.backend.core.sandbox.ProcessResult;
import ai.architech.backend.core.sandbox.ProjectExecutionCapability;
import ai.architech.backend.core.sandbox.ProjectExecutionTask;
import ai.architech.backend.core.sandbox.ProtectedPathException;
import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.core.sandbox.WorkspaceEscapeException;
import ai.architech.backend.core.sandbox.WorkspaceFileSystem;
import ai.architech.backend.core.sandbox.WorkspaceFrozenException;
import ai.architech.backend.core.toolexecution.ToolCapability;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Dispatches one requested tool call into the matching sandbox capability, checked against the
 * calling execution's {@link DeveloperToolCapabilityProfile} allowlist first - a hard filter
 * applied before the sandbox is ever touched, never a suggestion the loop could bypass (AIW-184).
 * Advertises (via {@link DeveloperToolSchemas}) and dispatches exactly three top-level tools:
 * {@code filesystem}, {@code project_execution}, {@code git_inspect} - one per {@link
 * ToolCapability} the tool-capability-profile actually grants Developer (dependency policy and
 * browser-runtime remain outside this ticket's own required scenarios).
 */
class DeveloperToolDispatcher {

	DeveloperToolCallOutcome dispatch(DeveloperToolCapabilityProfile profile, Workspace workspace, String toolName, JsonNode input) {
		return switch (toolName) {
			case "filesystem" -> dispatchFilesystem(profile, workspace, input);
			case "project_execution" -> dispatchProjectExecution(profile, workspace, input);
			case "git_inspect" -> dispatchGitInspect(profile, workspace, input);
			// The loop only ever advertises the three tools above - reaching here means the model
			// requested something never offered to it. Recorded (never silently dropped) using
			// FILESYSTEM as a fixed, arbitrary classification purely so the required non-null
			// ToolExecution.capability column has a value; the real detail (the actual unknown
			// name) lives in toolName/rawDiagnostics, and the call is always denied, never dispatched.
			default -> DeveloperToolCallOutcome.denied(ToolCapability.FILESYSTEM, "unknown tool '" + toolName + "' was never advertised");
		};
	}

	private DeveloperToolCallOutcome dispatchFilesystem(DeveloperToolCapabilityProfile profile, Workspace workspace, JsonNode input) {
		String operationName = input.path("operation").asString(null);
		FilesystemCapability operation = parseFilesystemCapability(operationName);
		if (operation == null) {
			return DeveloperToolCallOutcome.denied(ToolCapability.FILESYSTEM, "unknown filesystem operation '" + operationName + "'");
		}
		if (!profile.allowsFilesystemOperation(operation)) {
			return DeveloperToolCallOutcome.denied(
					ToolCapability.FILESYSTEM, "filesystem operation '" + operationName + "' is not in this profile's allowlist");
		}

		WorkspaceFileSystem filesystem = new WorkspaceFileSystem(workspace);
		String path = input.path("path").asString(null);
		try {
			String detail =
					switch (operation) {
						case LIST -> String.join("\n", filesystem.list(path));
						case READ -> filesystem.read(path);
						case SEARCH -> String.join("\n", filesystem.search(path, input.path("pattern").asString(null)));
						case WRITE -> {
							filesystem.write(path, input.path("content").asString(null));
							yield "wrote " + path;
						}
						case PATCH -> {
							filesystem.patch(path, input.path("oldContent").asString(null), input.path("newContent").asString(null));
							yield "patched " + path;
						}
						case MKDIR -> {
							filesystem.mkdir(path);
							yield "created directory " + path;
						}
						case MOVE -> {
							String destination = input.path("destination").asString(null);
							filesystem.move(path, destination);
							yield "moved " + path + " to " + destination;
						}
						case DELETE -> {
							filesystem.delete(path);
							yield "deleted " + path;
						}
					};
			return DeveloperToolCallOutcome.succeeded(ToolCapability.FILESYSTEM, detail);
		} catch (ProtectedPathException | WorkspaceEscapeException | WorkspaceFrozenException e) {
			return DeveloperToolCallOutcome.denied(ToolCapability.FILESYSTEM, e.getMessage());
		} catch (RuntimeException e) {
			return DeveloperToolCallOutcome.failed(ToolCapability.FILESYSTEM, e.getMessage());
		}
	}

	private DeveloperToolCallOutcome dispatchProjectExecution(DeveloperToolCapabilityProfile profile, Workspace workspace, JsonNode input) {
		String taskName = input.path("task").asString(null);
		ProjectExecutionTask task = parseProjectExecutionTask(taskName);
		if (task == null || task == ProjectExecutionTask.LOCAL_RUNTIME) {
			return DeveloperToolCallOutcome.denied(
					ToolCapability.PROJECT_EXECUTION, "unknown or unsupported project execution task '" + taskName + "'");
		}
		if (!profile.allowsProjectExecutionTask(task)) {
			return DeveloperToolCallOutcome.denied(
					ToolCapability.PROJECT_EXECUTION, "project execution task '" + taskName + "' is not in this profile's allowlist");
		}

		try {
			ProcessResult result = new ProjectExecutionCapability(workspace).run(task);
			String detail = "exit " + result.exitCode() + "\nstdout:\n" + result.stdout() + "\nstderr:\n" + result.stderr();
			// A nonzero exit is a Developer-owned failure (bad code, failing test) - reported
			// FAILED, never ERROR, which stays reserved for the sandbox itself malfunctioning
			// (caught below).
			return result.succeeded()
					? DeveloperToolCallOutcome.succeeded(ToolCapability.PROJECT_EXECUTION, detail)
					: DeveloperToolCallOutcome.failed(ToolCapability.PROJECT_EXECUTION, detail);
		} catch (RuntimeException e) {
			return DeveloperToolCallOutcome.errored(ToolCapability.PROJECT_EXECUTION, e.getMessage());
		}
	}

	private DeveloperToolCallOutcome dispatchGitInspect(DeveloperToolCapabilityProfile profile, Workspace workspace, JsonNode input) {
		String subcommand = input.path("command").asString(null);
		if (subcommand == null || !profile.allowsGitSubcommand(subcommand)) {
			return DeveloperToolCallOutcome.denied(
					ToolCapability.GIT_INSPECTION, "git subcommand '" + subcommand + "' is not in this profile's allowlist");
		}

		// GitInspectionCapability's own GitCommand enum is the actual, technically-enforced
		// allowlist (status/diff/diff-stat only) - a profile that somehow claimed to allow more
		// than that could still never reach a real process for anything else.
		Optional<ProcessResult> result = new GitInspectionCapability(workspace).runIfAllowed(subcommand);
		if (result.isEmpty()) {
			return DeveloperToolCallOutcome.denied(
					ToolCapability.GIT_INSPECTION, "git subcommand '" + subcommand + "' cannot be executed by this sandbox surface");
		}
		ProcessResult processResult = result.get();
		String detail = "exit " + processResult.exitCode() + "\n" + processResult.stdout();
		return processResult.succeeded()
				? DeveloperToolCallOutcome.succeeded(ToolCapability.GIT_INSPECTION, detail)
				: DeveloperToolCallOutcome.failed(ToolCapability.GIT_INSPECTION, detail);
	}

	private static FilesystemCapability parseFilesystemCapability(String operationName) {
		if (operationName == null) {
			return null;
		}
		try {
			return FilesystemCapability.valueOf(operationName.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static ProjectExecutionTask parseProjectExecutionTask(String taskName) {
		if (taskName == null) {
			return null;
		}
		try {
			return ProjectExecutionTask.valueOf(taskName.toUpperCase().replace('-', '_'));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
