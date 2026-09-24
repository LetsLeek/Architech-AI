package ai.architech.backend.core.sandbox;

/**
 * Thrown when a mutating filesystem operation is attempted against a {@link Workspace} that is
 * currently frozen (AIW-154) - after final Developer handoff, until an authorized correction
 * phase calls {@link Workspace#unfreeze}.
 */
public class WorkspaceFrozenException extends RuntimeException {

	public WorkspaceFrozenException(String relativePath) {
		super("workspace is frozen - '" + relativePath + "' cannot be written until an authorized correction phase reopens write authority");
	}
}
