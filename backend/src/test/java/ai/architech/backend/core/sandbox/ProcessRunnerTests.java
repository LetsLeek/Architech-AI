package ai.architech.backend.core.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link ProcessRunner} directly with fast, network-free, universally-available
 * commands ({@code git}, {@code sh}) rather than through a capability class - the timeout and
 * non-zero-exit paths aren't reachable through {@link GitInspectionCapability}'s own allowed
 * operations, which all succeed quickly against a real repo.
 */
class ProcessRunnerTests {

	@TempDir
	Path root;

	@Test
	void capturesStdoutAndASuccessfulExitCode() {
		ProcessResult result = ProcessRunner.run(new Workspace(root), List.of("git", "--version"), Duration.ofSeconds(10));

		assertThat(result.succeeded()).isTrue();
		assertThat(result.stdout()).contains("git version");
	}

	@Test
	void capturesANonZeroExitCodeAndStderrWithoutThrowing() {
		ProcessResult result =
				ProcessRunner.run(new Workspace(root), List.of("git", "not-a-real-subcommand"), Duration.ofSeconds(10));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.exitCode()).isNotZero();
	}

	@Test
	void runsScopedToTheWorkspaceRootAsItsWorkingDirectory() throws java.io.IOException {
		ProcessResult result = ProcessRunner.run(new Workspace(root), List.of("sh", "-c", "pwd"), Duration.ofSeconds(10));

		assertThat(result.succeeded()).isTrue();
		assertThat(result.stdout().strip()).isEqualTo(root.toRealPath().toString());
	}

	@Test
	void killsAndFailsAProcessThatExceedsItsTimeout() {
		assertThatThrownBy(() -> ProcessRunner.run(new Workspace(root), List.of("sleep", "5"), Duration.ofMillis(200)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("did not finish within");
	}
}
