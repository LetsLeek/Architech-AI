package ai.architech.backend.core.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * One isolated writable workspace root for exactly one AgentExecution (AIW-139's own
 * {@code DEV-ARCH-004}/{@code DEV-ARCH-005}: one workspace per execution, no host/root/other-
 * project escape). Every path a capability class touches must go through {@link #resolve} first
 * - it is the single point that proves a path stays inside this workspace, both lexically
 * (rejecting {@code ../..} traversal and absolute paths before touching the filesystem) and
 * physically (rejecting a symlink whose real target escapes the root, which lexical
 * normalization alone cannot catch).
 *
 * <p>Also tracks whether Developer write authority is currently frozen (AIW-154) - see
 * {@link #freeze}/{@link #unfreeze}.
 */
public final class Workspace {

	private final Path root;
	private volatile boolean frozen;

	public Workspace(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	/**
	 * Freezes normal Developer write authority (AIW-154): once frozen, every mutating
	 * {@link WorkspaceFileSystem} method throws {@link WorkspaceFrozenException} regardless of
	 * path, until {@link #unfreeze} is called. Read-only operations are unaffected - result
	 * validation and Runner verification still need to read the frozen state.
	 */
	public void freeze() {
		this.frozen = true;
	}

	/** Reopens write authority - only an authorized correction phase may call this (AIW-150/154). */
	public void unfreeze() {
		this.frozen = false;
	}

	public boolean isFrozen() {
		return frozen;
	}

	/**
	 * Resolves {@code relativePath} against this workspace's root, throwing
	 * {@link WorkspaceEscapeException} if it would resolve outside the root - lexically (before
	 * any filesystem access) or, for a path whose existing ancestor is itself a symlink, after
	 * resolving that symlink to its real target.
	 */
	public Path resolve(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			throw new WorkspaceEscapeException(String.valueOf(relativePath), "path must not be blank");
		}

		Path requested = Path.of(relativePath);
		if (requested.isAbsolute()) {
			throw new WorkspaceEscapeException(relativePath, "absolute paths are not allowed");
		}

		Path candidate = root.resolve(requested).normalize();
		if (!candidate.equals(root) && !candidate.startsWith(root)) {
			throw new WorkspaceEscapeException(relativePath, "path escapes the workspace root");
		}

		Path realRoot = toRealPath(root);
		Path realAncestor = toRealPath(longestExistingAncestor(candidate));
		if (!realAncestor.equals(realRoot) && !realAncestor.startsWith(realRoot)) {
			throw new WorkspaceEscapeException(relativePath, "path escapes the workspace root through a symlink");
		}

		return candidate;
	}

	/**
	 * True when {@code resolved} (already validated by {@link #resolve}) falls under one of this
	 * workspace's protected top-level entries ({@code .git}, {@code runner-metadata}) -
	 * off-limits to every filesystem capability regardless of read/write, since Git's own
	 * inspection-only capability is the sole authorized way to touch {@code .git} at all.
	 */
	public boolean isProtected(Path resolved) {
		Path relative = root.relativize(resolved);
		if (relative.getNameCount() == 0) {
			return false;
		}
		String firstSegment = relative.getName(0).toString();
		return firstSegment.equals(".git") || firstSegment.equals("runner-metadata");
	}

	private static Path longestExistingAncestor(Path candidate) {
		Path current = candidate;
		while (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
			Path parent = current.getParent();
			if (parent == null) {
				return current;
			}
			current = parent;
		}
		return current;
	}

	private static Path toRealPath(Path path) {
		try {
			return path.toRealPath();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to resolve real path for " + path, e);
		}
	}
}
