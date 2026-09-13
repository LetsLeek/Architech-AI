package ai.architech.backend.core.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses a real local git repository - proves real process execution, not just mocked plumbing. */
class GitInspectionCapabilityTests {

	@TempDir
	Path root;

	private GitInspectionCapability git;

	@BeforeEach
	void initRealGitRepo() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
		Files.writeString(root.resolve("README.md"), "hello\n");
		run(root, "git", "add", "README.md");
		run(root, "git", "commit", "--quiet", "-m", "initial commit");

		git = new GitInspectionCapability(new Workspace(root));
	}

	@Test
	void statusReportsARealUncommittedChange() throws IOException {
		Files.writeString(root.resolve("README.md"), "hello, changed\n");

		ProcessResult result = git.run(GitCommand.STATUS);

		assertThat(result.succeeded()).isTrue();
		assertThat(result.stdout()).contains("README.md");
	}

	@Test
	void diffReportsTheRealTextualChange() throws IOException {
		Files.writeString(root.resolve("README.md"), "hello, changed\n");

		ProcessResult result = git.run(GitCommand.DIFF);

		assertThat(result.succeeded()).isTrue();
		assertThat(result.stdout()).contains("-hello").contains("+hello, changed");
	}

	@Test
	void diffStatSummarizesTheRealChange() throws IOException {
		Files.writeString(root.resolve("README.md"), "hello, changed\n");

		ProcessResult result = git.run(GitCommand.DIFF_STAT);

		assertThat(result.succeeded()).isTrue();
		assertThat(result.stdout()).contains("README.md");
	}

	@Test
	void runIfAllowedDeniesEveryProhibitedGitOperationWithoutStartingAProcess() {
		assertThat(git.runIfAllowed("push")).isEmpty();
		assertThat(git.runIfAllowed("branch")).isEmpty();
		assertThat(git.runIfAllowed("checkout")).isEmpty();
		assertThat(git.runIfAllowed("merge")).isEmpty();
		assertThat(git.runIfAllowed("rebase")).isEmpty();
		assertThat(git.runIfAllowed("commit")).isEmpty();
		assertThat(git.runIfAllowed("force-push")).isEmpty();
	}

	@Test
	void runIfAllowedRunsTheThreeAllowedOperations() {
		assertThat(git.runIfAllowed("status")).isPresent();
		assertThat(git.runIfAllowed("diff")).isPresent();
		assertThat(git.runIfAllowed("diff-stat")).isPresent();
	}

	@Test
	void theClosedGitCommandEnumContainsExactlyTheThreeAllowedOperations() {
		assertThat(GitCommand.values()).containsExactlyInAnyOrder(GitCommand.STATUS, GitCommand.DIFF, GitCommand.DIFF_STAT);
	}

	@Test
	void noAllowedGitCommandInvokesARawNetworkOperation() {
		// rawOutbound: false - none of the three allowed operations touch a remote at all
		// (no fetch/pull/push/clone/remote), so there is nothing here for a network policy to
		// even need to intercept.
		for (GitCommand command : GitCommand.values()) {
			assertThat(command.processArgs())
					.noneMatch(arg -> List.of("fetch", "pull", "push", "clone", "remote").contains(arg));
		}
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
