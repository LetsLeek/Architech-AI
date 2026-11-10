package ai.architech.backend.core.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * AIW-231: {@code node_modules/.bin/*} shims (and any other scaffold symlink) must survive
 * materialization as actual symlinks, not get silently flattened into a plain-file copy of
 * their target's content - see {@link ScaffoldMaterializer}'s own javadoc for why the default
 * (link-following) behavior of {@code Files.isRegularFile}/{@code Files.copy} broke this for
 * years, invisible only because every caller happened to run {@code npm ci} right after (which
 * always rebuilds node_modules from scratch regardless).
 */
class ScaffoldMaterializerTests {

	@Test
	void preservesASymlinkAsASymlinkRatherThanCopyingItsTargetsContent(@TempDir Path scaffoldSource, @TempDir Path target)
			throws IOException {
		Path realFile = scaffoldSource.resolve("real.txt");
		Files.writeString(realFile, "actual content");
		Files.createSymbolicLink(scaffoldSource.resolve("link.txt"), realFile.getFileName());

		new ScaffoldMaterializer(fixedRoot(scaffoldSource)).materializeInto(target);

		Path materializedLink = target.resolve("link.txt");
		assertThat(Files.isSymbolicLink(materializedLink)).isTrue();
		assertThat(Files.readSymbolicLink(materializedLink)).isEqualTo(realFile.getFileName());
		assertThat(Files.readString(materializedLink)).isEqualTo("actual content");
	}

	@Test
	void copiesARegularFileNormally(@TempDir Path scaffoldSource, @TempDir Path target) throws IOException {
		Files.writeString(scaffoldSource.resolve("plain.txt"), "hello");

		new ScaffoldMaterializer(fixedRoot(scaffoldSource)).materializeInto(target);

		Path materialized = target.resolve("plain.txt");
		assertThat(Files.isSymbolicLink(materialized)).isFalse();
		assertThat(Files.readString(materialized)).isEqualTo("hello");
	}

	private ResourceLoader fixedRoot(Path root) {
		return new ResourceLoader() {
			@Override
			public Resource getResource(String location) {
				return new FileSystemResource(root);
			}

			@Override
			public ClassLoader getClassLoader() {
				return getClass().getClassLoader();
			}
		};
	}
}
