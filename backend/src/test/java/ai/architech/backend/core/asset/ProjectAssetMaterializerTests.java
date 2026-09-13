package ai.architech.backend.core.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.sandbox.WorkspaceEscapeException;
import ai.architech.backend.core.sandbox.WorkspaceFileSystem;
import ai.architech.backend.core.sandbox.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectAssetMaterializerTests {

	@TempDir
	Path root;

	@Test
	void materializesTheResolvedAssetsContentIntoTheWorkspace() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));
		byte[] content = "logo-bytes".getBytes(StandardCharsets.UTF_8);
		ProjectAsset asset = new ProjectAsset(UUID.randomUUID(), "assets/logo.png", "logo.png", "image/png", content);

		ProjectAssetMaterializer.materialize(asset, fs, "public/logo.png");

		assertThat(root.resolve("public/logo.png")).binaryContent().containsExactly(content);
	}

	@Test
	void reusesTheWorkspacesOwnPathContainmentSoMaterializationCannotEscapeTheSandbox() {
		WorkspaceFileSystem fs = new WorkspaceFileSystem(new Workspace(root));
		ProjectAsset asset = new ProjectAsset(
				UUID.randomUUID(), "assets/logo.png", "logo.png", "image/png",
				"logo-bytes".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> ProjectAssetMaterializer.materialize(asset, fs, "../../../etc/evil.png"))
				.isInstanceOf(WorkspaceEscapeException.class);
	}
}
