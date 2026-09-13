package ai.architech.backend.core.candidate;

import java.util.UUID;

/**
 * Thrown when the workspace's current git-tracked content no longer matches the
 * {@code FrozenHandoffSnapshot} Runner Verification actually evaluated (AIW-145's "Verified
 * workspace state and persisted repositoryStateRef are deterministically equivalent") -
 * something changed the tracked tree between verification and Candidate persistence, so
 * persisting now would not be persisting the exact state that was verified.
 */
public class RepositoryStateMismatchException extends RuntimeException {

	public RepositoryStateMismatchException(UUID agentExecutionId) {
		super("workspace state for execution " + agentExecutionId
				+ " no longer matches the frozen snapshot Runner Verification evaluated");
	}
}
