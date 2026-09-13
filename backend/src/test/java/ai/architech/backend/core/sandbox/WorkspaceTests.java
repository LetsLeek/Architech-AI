package ai.architech.backend.core.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTests {

	@TempDir
	Path root;

	@Test
	void resolvesAnOrdinaryRelativePathInsideTheWorkspace() throws IOException {
		Files.createDirectories(root.resolve("src"));
		Workspace workspace = new Workspace(root);

		Path resolved = workspace.resolve("src/App.tsx");

		assertThat(resolved).isEqualTo(root.resolve("src/App.tsx"));
	}

	@Test
	void rejectsLexicalPathTraversalOutOfTheWorkspace() {
		Workspace workspace = new Workspace(root);

		assertThatThrownBy(() -> workspace.resolve("../../../etc/passwd"))
				.isInstanceOf(WorkspaceEscapeException.class);
	}

	@Test
	void rejectsAnAbsolutePath() {
		Workspace workspace = new Workspace(root);

		assertThatThrownBy(() -> workspace.resolve("/etc/passwd")).isInstanceOf(WorkspaceEscapeException.class);
	}

	@Test
	void rejectsASymlinkWhoseRealTargetEscapesTheWorkspace(@TempDir Path outside) throws IOException {
		Path secretOutside = outside.resolve("secret.txt");
		Files.writeString(secretOutside, "not yours");
		Files.createSymbolicLink(root.resolve("escape-link"), secretOutside);
		Workspace workspace = new Workspace(root);

		assertThatThrownBy(() -> workspace.resolve("escape-link")).isInstanceOf(WorkspaceEscapeException.class);
	}

	@Test
	void rejectsATraversalThroughASymlinkedDirectory(@TempDir Path outside) throws IOException {
		Path outsideDir = Files.createDirectories(outside.resolve("other-project"));
		Files.writeString(outsideDir.resolve("file.txt"), "not yours either");
		Files.createSymbolicLink(root.resolve("linked-dir"), outsideDir);
		Workspace workspace = new Workspace(root);

		assertThatThrownBy(() -> workspace.resolve("linked-dir/file.txt")).isInstanceOf(WorkspaceEscapeException.class);
	}

	@Test
	void treatsASiblingWorkspaceAsCompletelyUnreachable(@TempDir Path otherProjectRoot) throws IOException {
		Files.writeString(otherProjectRoot.resolve("customer-secret.txt"), "someone else's project");
		Workspace workspace = new Workspace(root);

		// Even naming the sibling's absolute path, or trying to climb out and back in via "..",
		// must fail - two workspaces never share reachability just because they're on the same
		// filesystem.
		assertThatThrownBy(() -> workspace.resolve(otherProjectRoot.resolve("customer-secret.txt").toString()))
				.isInstanceOf(WorkspaceEscapeException.class);
		assertThatThrownBy(() -> workspace.resolve("../" + otherProjectRoot.getFileName() + "/customer-secret.txt"))
				.isInstanceOf(WorkspaceEscapeException.class);
	}

	@Test
	void identifiesGitAndRunnerMetadataAsProtected() throws IOException {
		Files.createDirectories(root.resolve(".git"));
		Files.createDirectories(root.resolve("runner-metadata"));
		Files.createDirectories(root.resolve("src"));
		Workspace workspace = new Workspace(root);

		assertThat(workspace.isProtected(root.resolve(".git/config"))).isTrue();
		assertThat(workspace.isProtected(root.resolve("runner-metadata/execution.json"))).isTrue();
		assertThat(workspace.isProtected(root.resolve("src/App.tsx"))).isFalse();
	}
}
