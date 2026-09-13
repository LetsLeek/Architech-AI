package ai.architech.backend.core.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFileSystemTests {

	@TempDir
	Path root;

	@Test
	void writesReadsListsAndDeletesWithinTheWorkspace() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		fs.write("src/App.tsx", "export const App = () => null;");
		assertThat(fs.read("src/App.tsx")).isEqualTo("export const App = () => null;");
		assertThat(fs.list("src")).containsExactly("App.tsx");

		fs.delete("src/App.tsx");
		assertThat(fs.list("src")).isEmpty();
	}

	@Test
	void patchesExactlyOneUnambiguousMatch() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));
		fs.write("src/HomePage.tsx", "export function HomePage() { return <main>Old</main> }");

		fs.patch("src/HomePage.tsx", "<main>Old</main>", "<main>New</main>");

		assertThat(fs.read("src/HomePage.tsx")).contains("<main>New</main>").doesNotContain("Old<");
	}

	@Test
	void rejectsAnAmbiguousPatchMatch() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));
		fs.write("src/dup.tsx", "same same");

		assertThatThrownBy(() -> fs.patch("src/dup.tsx", "same", "different")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void movesAFileWithinTheWorkspace() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));
		fs.write("src/Old.tsx", "content");

		fs.move("src/Old.tsx", "src/New.tsx");

		assertThat(fs.list("src")).containsExactly("New.tsx");
		assertThat(fs.read("src/New.tsx")).isEqualTo("content");
	}

	@Test
	void deniesReadingProtectedGitInternals() throws IOException {
		Files.createDirectories(root.resolve(".git"));
		Files.writeString(root.resolve(".git/config"), "[core]");
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		assertThatThrownBy(() -> fs.read(".git/config")).isInstanceOf(ProtectedPathException.class);
	}

	@Test
	void deniesWritingIntoProtectedRunnerMetadata() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		assertThatThrownBy(() -> fs.write("runner-metadata/execution.json", "{}"))
				.isInstanceOf(ProtectedPathException.class);
	}

	@Test
	void deniesDeletingProtectedGitInternals() throws IOException {
		Files.createDirectories(root.resolve(".git"));
		Files.writeString(root.resolve(".git/HEAD"), "ref: refs/heads/main");
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		assertThatThrownBy(() -> fs.delete(".git/HEAD")).isInstanceOf(ProtectedPathException.class);
	}

	@Test
	void searchFindsAMatchingFileByContent() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));
		fs.write("src/App.tsx", "export function App() { return null; }");
		fs.write("src/Other.tsx", "export function Other() { return null; }");

		assertThat(fs.search("src", "function App")).containsExactly("src/App.tsx");
	}

	@Test
	void searchReturnsNoMatchesWhenThePatternIsAbsent() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));
		fs.write("src/App.tsx", "export function App() { return null; }");

		assertThat(fs.search("src", "nonexistentPattern")).isEmpty();
	}

	@Test
	void searchNeverReturnsProtectedGitInternalsEvenWhenTheyMatch() throws IOException {
		Files.createDirectories(root.resolve(".git"));
		Files.writeString(root.resolve(".git/config"), "needle");
		Files.writeString(root.resolve("app.txt"), "needle");
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		assertThat(fs.search(".", "needle")).containsExactly("app.txt");
	}

	@Test
	void deniesSearchOutsideTheWorkspace() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		assertThatThrownBy(() -> fs.search("../../../etc", "root")).isInstanceOf(WorkspaceEscapeException.class);
	}

	@Test
	void mkdirCreatesNestedDirectories() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		fs.mkdir("src/components/nested");

		assertThat(fs.list("src/components")).containsExactly("nested");
	}

	@Test
	void deniesPathTraversalThroughEveryFilesystemOperation() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));

		assertThatThrownBy(() -> fs.read("../../../etc/passwd")).isInstanceOf(WorkspaceEscapeException.class);
		assertThatThrownBy(() -> fs.write("../../../tmp/evil.txt", "x")).isInstanceOf(WorkspaceEscapeException.class);
		assertThatThrownBy(() -> fs.list("..")).isInstanceOf(WorkspaceEscapeException.class);
	}
}
