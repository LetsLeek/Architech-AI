package ai.architech.backend.core.sandbox;

/**
 * Thrown when a filesystem capability targets {@code .git} or {@code runner-metadata} - distinct
 * from {@link WorkspaceEscapeException} because this path IS inside the workspace, it is simply
 * off-limits to every filesystem capability (Git's own inspection-only capability is the sole
 * authorized way to touch {@code .git} at all).
 */
public class ProtectedPathException extends RuntimeException {

	public ProtectedPathException(String relativePath) {
		super("path '" + relativePath + "' is protected and not reachable through filesystem capabilities");
	}
}
