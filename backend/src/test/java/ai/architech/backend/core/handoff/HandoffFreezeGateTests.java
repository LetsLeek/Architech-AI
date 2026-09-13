package ai.architech.backend.core.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.core.sandbox.WorkspaceFileSystem;
import ai.architech.backend.core.sandbox.WorkspaceFrozenException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses a real local git repository, exactly like AIW-158's own {@code SecretScanGateTests} - the snapshot identity needs real tracked state. */
class HandoffFreezeGateTests {

	@TempDir
	Path root;

	private final HandoffFreezeGate gate = new HandoffFreezeGate();

	@BeforeEach
	void initRealGitRepo() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
	}

	@Test
	void freezingComputesASnapshotAndFreezesTheWorkspace() throws IOException, InterruptedException {
		writeAndTrack("src/App.tsx", "export const App = () => null;");
		Workspace workspace = new Workspace(root);

		FrozenHandoffSnapshot snapshot = gate.freeze(workspace);

		assertThat(snapshot.snapshotId()).isNotBlank();
		assertThat(snapshot.frozenAt()).isNotNull();
		assertThat(workspace.isFrozen()).isTrue();
	}

	@Test
	void forbidsDeveloperWritesAfterFreezing() throws IOException, InterruptedException {
		writeAndTrack("src/App.tsx", "export const App = () => null;");
		Workspace workspace = new Workspace(root);
		WorkspaceFileSystem fs = new WorkspaceFileSystem(workspace);

		gate.freeze(workspace);

		assertThatThrownBy(() -> fs.write("src/App.tsx", "changed")).isInstanceOf(WorkspaceFrozenException.class);
	}

	@Test
	void aCorrectionReopensWritesAndReFreezingProducesANewSnapshot() throws IOException, InterruptedException {
		writeAndTrack("src/App.tsx", "export const App = () => null;");
		Workspace workspace = new Workspace(root);
		WorkspaceFileSystem fs = new WorkspaceFileSystem(workspace);
		FrozenHandoffSnapshot firstSnapshot = gate.freeze(workspace);

		gate.unfreezeForCorrection(workspace);
		fs.write("src/App.tsx", "export const App = () => \"corrected\";");
		track("src/App.tsx");
		FrozenHandoffSnapshot secondSnapshot = gate.freeze(workspace);

		assertThat(secondSnapshot.snapshotId()).isNotEqualTo(firstSnapshot.snapshotId());
		assertThat(gate.matchesCurrentState(workspace, secondSnapshot)).isTrue();
		assertThat(gate.matchesCurrentState(workspace, firstSnapshot)).isFalse();
	}

	@Test
	void detectsAMutationIntroducedAfterFreezingWithoutGoingThroughAnAuthorizedCorrection() throws IOException, InterruptedException {
		writeAndTrack("src/App.tsx", "export const App = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = gate.freeze(workspace);

		// Simulates a final-verification tool writing into tracked state directly, bypassing the
		// Workspace's own frozen check entirely (exactly what AIW-154's own "detect source-
		// controlled mutations introduced by final verification tools" acceptance criterion names).
		Files.writeString(root.resolve("src/App.tsx"), "mutated by a verification tool");
		track("src/App.tsx");

		assertThat(gate.matchesCurrentState(workspace, snapshot)).isFalse();
	}

	@Test
	void ignoresUntrackedTransientOutputWhenComputingTheSnapshot() throws IOException, InterruptedException {
		writeAndTrack("src/App.tsx", "export const App = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = gate.freeze(workspace);

		// An untracked build artifact (node_modules, dist, ...) never changes the snapshot identity.
		Files.createDirectories(root.resolve("node_modules/some-package"));
		Files.writeString(root.resolve("node_modules/some-package/index.js"), "module.exports = {};");

		assertThat(gate.matchesCurrentState(workspace, snapshot)).isTrue();
	}

	private void writeAndTrack(String relativePath, String content) throws IOException, InterruptedException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		track(relativePath);
	}

	private void track(String relativePath) throws IOException, InterruptedException {
		run(root, "git", "add", relativePath);
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
