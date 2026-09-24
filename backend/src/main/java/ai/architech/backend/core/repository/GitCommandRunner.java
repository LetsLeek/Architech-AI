package ai.architech.backend.core.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a real {@code git} process scoped to a Core-managed directory (a project's own
 * repository root, or a fresh clone target) - deliberately separate from
 * {@code core.sandbox.ProcessRunner}: that class exists for the Developer's own bounded,
 * capability-gated tool surface, while repository provisioning is explicitly Core/Workflow
 * authority the Developer never touches (AIW-153's own scope). Drains stdout/stderr
 * concurrently to avoid the classic ProcessBuilder pipe deadlock, exactly like
 * {@code ProcessRunner} does.
 */
final class GitCommandRunner {

	private GitCommandRunner() {}

	static String run(Path cwd, String... args) {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.addAll(List.of(args));

		Process process;
		try {
			// `args` is always a fixed set of literal strings from DevelopmentBaseProvisioner's
			// own call sites (init/config/add/commit/rev-parse/clone with fixed or
			// platform-generated paths) - never user/model-controlled text.
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		} catch (IOException e) {
			throw new RepositoryProvisioningException("Failed to start git " + String.join(" ", args), e);
		}

		Drain stdout = new Drain(process.getInputStream());
		Drain stderr = new Drain(process.getErrorStream());
		Thread stdoutThread = Thread.ofVirtual().start(stdout);
		Thread stderrThread = Thread.ofVirtual().start(stderr);

		int exitCode;
		try {
			exitCode = process.waitFor();
			stdoutThread.join();
			stderrThread.join();
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new RepositoryProvisioningException("Interrupted while running git " + String.join(" ", args), e);
		}

		if (exitCode != 0) {
			throw new RepositoryProvisioningException(
					"git " + String.join(" ", args) + " failed with exit code " + exitCode + ": " + stderr.result());
		}
		return stdout.result();
	}

	private static final class Drain implements Runnable {
		private final java.io.InputStream in;
		private volatile String result = "";

		Drain(java.io.InputStream in) {
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
