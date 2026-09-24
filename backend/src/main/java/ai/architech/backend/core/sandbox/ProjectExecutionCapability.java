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

	/**
	 * Starts the local dev server; the caller owns the returned handle and must stop it itself -
	 * and must stop its descendants too: {@code npm run dev} forks its own {@code vite} child
	 * process, so destroying only this returned handle leaves that child running and still bound
	 * to its port (use {@code Process.descendants()} to reach it, exactly the way
	 * {@code core.verification.LocalRuntimeSmokeRunner} does). Output is discarded rather than
	 * left as the default {@code PIPE} - the classic ProcessBuilder deadlock ({@link ProcessRunner}
	 * drains bounded tasks' output concurrently for the same reason) is a real risk here
	 * specifically: a dev server runs indefinitely and keeps logging (HMR, request activity) for
	 * as long as a caller's browser smoke check keeps it alive, so an undrained pipe would
	 * eventually fill and block the server from ever responding.
	 */
	public Process startLocalRuntime() {
		try {
			// commandFor(...) always returns a fixed List.of(...) literal keyed off the closed
			// ProjectExecutionTask enum - never caller-supplied text, matching ProcessRunner's
			// own identical justification.
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			return new ProcessBuilder(commandFor(ProjectExecutionTask.LOCAL_RUNTIME))
					.directory(workspace.root().toFile())
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
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
			// `--host 127.0.0.1` pins Vite's dev-server bind address deterministically - without
			// it, Vite's own default ("localhost") resolves to whatever a given host's resolver
			// order happens to prefer (observed IPv6-only on some Linux hosts), which a plain
			// java.net.HttpURLConnection to "localhost" does not reliably reach.
			case LOCAL_RUNTIME -> List.of("npm", "run", "dev", "--", "--host", "127.0.0.1");
		};
	}
}
