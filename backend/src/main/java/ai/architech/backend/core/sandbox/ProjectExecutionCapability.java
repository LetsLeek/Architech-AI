package ai.architech.backend.core.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;

/**
 * Runs one of the six approved project-execution tasks scoped to one {@link Workspace}. Every
 * task maps to exactly one fixed npm invocation - the same commands AIW-138's Development Base
 * scaffold already proves work end to end (its own {@code package.json} scripts), reused
 * unchanged here rather than re-verified, since what this class itself is responsible for is the
 * dispatch/containment mechanism, not re-proving the scaffold's own build correctness.
 *
 * <p>{@link ProjectExecutionTask#LOCAL_RUNTIME} is handled separately from the other five: it
 * starts a long-running dev server rather than a command that exits on its own, so it has its
 * own start/stop lifecycle rather than going through {@link #run}'s bounded wait.
 */
public final class ProjectExecutionCapability {

	private static final Duration BOUNDED_TASK_TIMEOUT = Duration.ofMinutes(5);

	private final Workspace workspace;

	public ProjectExecutionCapability(Workspace workspace) {
		this.workspace = workspace;
	}

	/** {@code task} must be one of the five bounded tasks - use {@link #startLocalRuntime()} for {@code LOCAL_RUNTIME}. */
	public ProcessResult run(ProjectExecutionTask task) {
		if (task == ProjectExecutionTask.LOCAL_RUNTIME) {
			throw new IllegalArgumentException("LOCAL_RUNTIME does not exit on its own - use startLocalRuntime() instead");
		}
		return ProcessRunner.run(workspace, commandFor(task), BOUNDED_TASK_TIMEOUT);
	}

	/** Starts the local dev server; the caller owns the returned handle and must stop it itself. */
	public Process startLocalRuntime() {
		try {
			// commandFor(...) always returns a fixed List.of(...) literal keyed off the closed
			// ProjectExecutionTask enum - never caller-supplied text, matching ProcessRunner's
			// own identical justification.
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			return new ProcessBuilder(commandFor(ProjectExecutionTask.LOCAL_RUNTIME))
					.directory(workspace.root().toFile())
					.start();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to start local runtime", e);
		}
	}

	static List<String> commandFor(ProjectExecutionTask task) {
		return switch (task) {
			case INSTALL -> List.of("npm", "ci");
			case TYPECHECK -> List.of("npm", "run", "typecheck");
			case LINT -> List.of("npm", "run", "lint");
			case TEST -> List.of("npm", "run", "test");
			case BUILD -> List.of("npm", "run", "build");
			case LOCAL_RUNTIME -> List.of("npm", "run", "dev");
		};
	}
}
