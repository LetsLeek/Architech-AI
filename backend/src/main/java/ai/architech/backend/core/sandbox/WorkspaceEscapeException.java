package ai.architech.backend.core.sandbox;

/**
 * Thrown the moment a requested path would resolve outside its {@link Workspace} root - whether
 * by lexical traversal ({@code ../..}), an absolute path, or a symlink whose target escapes the
 * root. Never silently clamped/rewritten to a safe path; the caller gets a hard failure.
 */
public class WorkspaceEscapeException extends RuntimeException {

	public WorkspaceEscapeException(String requestedPath, String reason) {
		super("path '" + requestedPath + "' is not allowed: " + reason);
	}
}
