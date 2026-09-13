package ai.architech.backend.core.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Copies AIW-138's Development Base scaffold (the design-neutral platform template) into a
 * target repository root. Operates on the real filesystem directory backing the classpath
 * resource (rather than Spring's Ant-style resource-pattern matching, which is a poor fit for
 * "copy this entire real directory tree byte for byte") - correct here because this project's
 * Maven build always copies {@code project-types/} onto the classpath as plain files
 * ({@code copy-project-type-definitions} in {@code backend/pom.xml}), never packages it inside a
 * jar the way a deployed artifact might.
 */
@Component
public class ScaffoldMaterializer {

	private static final String SCAFFOLD_CLASSPATH_ROOT =
			"classpath:project-types/website/agents/developer-agent/scaffold/";

	private final ResourceLoader resourceLoader;

	ScaffoldMaterializer(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public void materializeInto(Path targetRoot) {
		Path scaffoldRoot = resolveScaffoldRoot();
		try (Stream<Path> files = Files.walk(scaffoldRoot)) {
			files.filter(Files::isRegularFile).forEach(source -> copyOne(scaffoldRoot, source, targetRoot));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to walk scaffold root " + scaffoldRoot, e);
		}
	}

	private void copyOne(Path scaffoldRoot, Path source, Path targetRoot) {
		Path relative = scaffoldRoot.relativize(source);
		Path destination = targetRoot.resolve(relative);
		try {
			Files.createDirectories(destination.getParent());
			Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to materialize scaffold file " + relative, e);
		}
	}

	private Path resolveScaffoldRoot() {
		try {
			return resourceLoader.getResource(SCAFFOLD_CLASSPATH_ROOT).getFile().toPath();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to resolve scaffold root " + SCAFFOLD_CLASSPATH_ROOT, e);
		}
	}
}
