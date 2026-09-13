package ai.architech.backend.core.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes a fixed, caller-supplied argument list scoped to one {@link Workspace} - the only way
 * any capability in this package ever spawns a process. There is no method anywhere that accepts
 * a single shell-interpreted command string (V1's {@code unrestrictedShell: false}): callers
 * always pass an already-tokenized {@code List<String>} built from a closed enum
 * ({@link GitCommand}, {@link ProjectExecutionTask}), never raw user/model text.
 */
final class ProcessRunner {

	private ProcessRunner() {}

	static ProcessResult run(Workspace workspace, List<String> command, Duration timeout) {
		Process process;
		try {
			// `command` is never a user/model-controlled string here - every caller (GitCommand's
			// own fixed processArgs(), ProjectExecutionCapability.commandFor()) builds it from a
			// closed enum's List.of(...) literal, exactly the structural guarantee this package
			// exists to provide (no unrestrictedShell). Semgrep can't trace that provenance
			// across the two call sites, but there is no code path anywhere in this package that
			// forwards caller-supplied text into a process argument list.
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			process = new ProcessBuilder(command)
					.directory(workspace.root().toFile())
					.start();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to start process " + command, e);
		}

		// Drained concurrently, not sequentially: a process that fills its stdout pipe before
		// exiting would otherwise deadlock against us still blocked reading stderr (or vice
		// versa) - the classic ProcessBuilder pitfall.
		StreamDrain stdoutDrain = new StreamDrain(process.getInputStream());
		StreamDrain stderrDrain = new StreamDrain(process.getErrorStream());
		Thread stdoutThread = Thread.ofVirtual().start(stdoutDrain);
		Thread stderrThread = Thread.ofVirtual().start(stderrDrain);

		boolean finishedInTime;
		try {
			finishedInTime = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for process " + command, e);
		}

		if (!finishedInTime) {
			process.destroyForcibly();
			throw new IllegalStateException("Process " + command + " did not finish within " + timeout);
		}

		try {
			stdoutThread.join(timeout.toMillis());
			stderrThread.join(timeout.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while draining process output for " + command, e);
		}

		return new ProcessResult(process.exitValue(), stdoutDrain.result(), stderrDrain.result());
	}

	/** Reads one stream to completion on its own thread, exposing the accumulated text once done. */
	private static final class StreamDrain implements Runnable {
		private final java.io.InputStream in;
		private volatile String result = "";

		StreamDrain(java.io.InputStream in) {
			this.in = in;
		}

		@Override
		public void run() {
			try {
				result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				result = "<failed to read stream: " + e.getMessage() + ">";
			}
		}

		String result() {
			return result;
		}
	}
}
