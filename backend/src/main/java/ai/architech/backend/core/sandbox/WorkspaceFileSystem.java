package ai.architech.backend.core.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * The bounded filesystem tool surface ({@link FilesystemCapability}) exposed to one Developer
 * execution. Every method resolves its path argument(s) through {@link Workspace#resolve}
 * first, so a caller structurally cannot reach outside the workspace no matter what string it
 * supplies - there is no raw {@link java.io.File}/{@link Path} escape hatch anywhere in this
 * class. Mutating operations additionally reject {@link Workspace#isProtected} targets.
 */
public final class WorkspaceFileSystem {

	private final Workspace workspace;

	public WorkspaceFileSystem(Workspace workspace) {
		this.workspace = workspace;
	}

	public List<String> list(String relativeDir) {
		Path resolved = resolveReadable(relativeDir);
		try (Stream<Path> entries = Files.list(resolved)) {
			return entries.map(path -> path.getFileName().toString()).sorted().toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to list " + relativeDir, e);
		}
	}

	public String read(String relativePath) {
		Path resolved = resolveReadable(relativePath);
		try {
			return Files.readString(resolved, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + relativePath, e);
		}
	}

	/** Minimal substring search across every file under {@code relativeDir} (no binary/glob filtering in V1). */
	public List<String> search(String relativeDir, String pattern) {
		Path resolved = resolveReadable(relativeDir);
		try (Stream<Path> entries = Files.walk(resolved)) {
			return entries
					.filter(Files::isRegularFile)
					.filter(path -> !workspace.isProtected(path))
					.filter(path -> containsPattern(path, pattern))
					.map(path -> workspace.root().relativize(path).toString())
					.sorted()
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to search " + relativeDir, e);
		}
	}

	public void write(String relativePath, String content) {
		Path resolved = resolveWritable(relativePath);
		try {
			Files.createDirectories(resolved.getParent());
			Files.writeString(resolved, content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write " + relativePath, e);
		}
	}

	/** Same containment/protected-path guarantees as {@link #write} - for binary content (e.g. authorized project assets, AIW-152). */
	public void writeBytes(String relativePath, byte[] content) {
		Path resolved = resolveWritable(relativePath);
		try {
			Files.createDirectories(resolved.getParent());
			Files.write(resolved, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write " + relativePath, e);
		}
	}

	/** Replaces exactly one occurrence of {@code oldContent} with {@code newContent}; ambiguous or absent is an error. */
	public void patch(String relativePath, String oldContent, String newContent) {
		String current = read(relativePath);
		int firstIndex = current.indexOf(oldContent);
		if (firstIndex < 0) {
			throw new IllegalArgumentException("patch target text not found in " + relativePath);
		}
		if (current.indexOf(oldContent, firstIndex + oldContent.length()) >= 0) {
			throw new IllegalArgumentException("patch target text is ambiguous (multiple matches) in " + relativePath);
		}
		write(relativePath, current.substring(0, firstIndex) + newContent + current.substring(firstIndex + oldContent.length()));
	}

	public void mkdir(String relativeDir) {
		Path resolved = resolveWritable(relativeDir);
		try {
			Files.createDirectories(resolved);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create directory " + relativeDir, e);
		}
	}

	public void move(String fromRelative, String toRelative) {
		Path from = resolveWritable(fromRelative);
		Path to = resolveWritable(toRelative);
		try {
			Files.createDirectories(to.getParent());
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to move " + fromRelative + " to " + toRelative, e);
		}
	}

	public void delete(String relativePath) {
		Path resolved = resolveWritable(relativePath);
		try {
			Files.delete(resolved);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to delete " + relativePath, e);
		}
	}

	private Path resolveReadable(String relativePath) {
		Path resolved = workspace.resolve(relativePath);
		if (workspace.isProtected(resolved)) {
			throw new ProtectedPathException(relativePath);
		}
		return resolved;
	}

	private Path resolveWritable(String relativePath) {
		if (workspace.isFrozen()) {
			throw new WorkspaceFrozenException(relativePath);
		}
		// Same rule as readable today (protected paths are never reachable through the
		// filesystem surface at all), kept as a separate method so a future, stricter
		// write-only rule doesn't have to touch every call site.
		return resolveReadable(relativePath);
	}

	private static boolean containsPattern(Path path, String pattern) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8).contains(pattern);
		} catch (IOException e) {
			return false; // unreadable/binary file - not a match, not a hard failure for the whole search
		}
	}
}
