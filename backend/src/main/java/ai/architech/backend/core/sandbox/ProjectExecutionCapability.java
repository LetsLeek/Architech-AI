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
	 * AIW-229: the strict, read-only-with-respect-to-the-lockfile install AIW-140's final
	 * reproducibility requirement actually needs - deliberately {@code npm ci}, never {@code npm
	 * install}, and deliberately its own method rather than a {@link ProjectExecutionTask} value:
	 * {@code ProjectExecutionTask.INSTALL} is reachable from the Developer Agent's own tool calls
	 * (see {@code DeveloperToolSchemas}), and giving the model any path to this strict behavior
	 * would reopen exactly the failure {@link #commandFor}'s own comment on {@code INSTALL}
	 * describes - this call exists only for {@code AuthoritativeRunnerVerifier}'s own handoff gate,
	 * which must confirm the final candidate installs from exactly its own committed manifest and
	 * lockfile, no reconciliation allowed.
	 */
	public ProcessResult runStrictReproducibilityInstall() {
		return ProcessRunner.run(workspace, List.of("npm", "ci"), BOUNDED_TASK_TIMEOUT);
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
			// AIW-229: was `npm ci`, found live to fail deterministically - and unfixably from the
			// Developer Agent's own side - every single time it legitimately edited package.json
			// (e.g. adding a self-hosted font package per modules/04-responsive-and-styling.md's own
			// guidance) and then re-ran this same INSTALL task: `npm ci` only ever installs from an
			// already-coherent lockfile, it never reconciles one, so it fails with
			// EUSAGE/"Missing: ... from lock file" against any lockfile it wasn't already in sync
			// with. The model has no way to ask for a different npm subcommand - DeveloperToolSchemas
			// only ever exposes the closed task name "install" - so this could never have been fixed
			// by instructing the model differently, only by changing what this one capability
			// actually runs. `npm install` reconciles the lockfile to the manifest (a no-op when
			// nothing changed, exactly a fresh/unedited checkout's own case), so it is a strict
			// superset of what `npm ci` could do here. The separate reproducibility requirement this
			// existed to protect (AIW-140: the final candidate must be npm-ci-installable from
			// exactly what it commits) is unaffected - AuthoritativeRunnerVerifier's own handoff gate
			// still runs real `npm ci` directly, independently of this capability, as the final check.
			case INSTALL -> List.of("npm", "install");
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
