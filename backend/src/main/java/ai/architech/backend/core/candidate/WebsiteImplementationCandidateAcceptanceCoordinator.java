package ai.architech.backend.core.candidate;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.sandbox.Workspace;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single retry-safe entry point from a PASSed authoritative Runner Verification to a
 * {@code SUCCEEDED} {@link AgentExecution} (AIW-159) - composes {@link
 * WebsiteImplementationCandidatePromoter} (AIW-145, itself already idempotent per {@code
 * agentExecutionId}) with the execution's own state transition so that "one durable accepted
 * Candidate exists" is true <em>before</em> {@code SUCCESS} is ever committed.
 *
 * <p>There is no database transaction spanning both writes, and deliberately so: the workspace's
 * git-tracked tree lives entirely outside the database, so no transaction could make the two
 * atomic anyway. Safety instead comes from {@link #accept} being safe to re-invoke from scratch
 * after a process failure at any point, never from wrapping it in {@code @Transactional}:
 *
 * <ul>
 *   <li>if {@code execution} is already {@code SUCCEEDED}, its Candidate - which by construction
 *       must already exist, see below - is looked up and returned unchanged, so a duplicate
 *       delivery of an already-completed acceptance is a pure no-op;
 *   <li>otherwise the Candidate is promoted first (idempotent: a Candidate already persisted by
 *       an earlier, interrupted attempt is reused rather than duplicated, and reuses that
 *       attempt's own {@code repositoryStateRef} rather than re-deriving one from whatever the
 *       workspace looks like now - "retry never creates a divergent repository state") and only
 *       once that call has returned a durably persisted row does {@link AgentExecution#succeed()}
 *       run. A process failure between the two leaves {@code execution} at {@code RUNNING} with
 *       its Candidate already safely persisted; simply calling {@link #accept} again finds that
 *       Candidate through the same idempotent lookup and completes the status transition - this
 *       is what makes "crash after Candidate persistence but before status update recovers
 *       deterministically to SUCCESS" true.
 * </ul>
 *
 * <p>A failure while promoting - a repository-store failure reading the workspace's git-tracked
 * state ({@code HandoffFreezeGate}, surfaced as an unchecked exception), a non-duplicate Candidate
 * DB persistence failure, or a {@link RepositoryStateMismatchException} - is a Core/infrastructure
 * malfunction, never a Developer-owned defect: this is the boundary that classifies it as such by
 * transitioning {@code execution} to {@link AgentExecutionStatus#ERROR} before rethrowing the
 * original exception unchanged. The workspace itself is never touched by this class on any path -
 * nothing here, or anywhere else in Core today, ever deletes a {@link Workspace} - so "repository
 * state remains retained/reachable when Candidate DB persistence temporarily fails" holds by
 * construction rather than by any cleanup ordering this class would otherwise have to get right.
 *
 * <p>A retry after this class has already terminated {@code execution} as {@code ERROR} is
 * deliberately not this class's concern: exactly like {@link AgentExecution#fail}, {@code error}
 * is a terminal state (AIW-38's "a retry is always a new AgentExecution, never a mutation of a
 * previous one"), so recovering from an observed (non-crash) infrastructure failure means a
 * Workflow-initiated retry against a new execution, the same as any other Core/infra failure in
 * this codebase - not an unbounded internal retry loop here.
 */
@Component
public class WebsiteImplementationCandidateAcceptanceCoordinator {

	private final WebsiteImplementationCandidatePromoter promoter;
	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final AgentExecutionRepository agentExecutionRepository;

	WebsiteImplementationCandidateAcceptanceCoordinator(
			WebsiteImplementationCandidatePromoter promoter,
			WebsiteImplementationCandidateRepository candidateRepository,
			AgentExecutionRepository agentExecutionRepository) {
		this.promoter = promoter;
		this.candidateRepository = candidateRepository;
		this.agentExecutionRepository = agentExecutionRepository;
	}

	public WebsiteImplementationCandidate accept(
			AgentExecution execution,
			UUID projectId,
			String developerResultJson,
			String executionInputJson,
			Workspace workspace,
			FrozenHandoffSnapshot snapshot) {
		if (execution.getStatus() == AgentExecutionStatus.SUCCEEDED) {
			return candidateRepository
					.findByAgentExecutionId(execution.getId())
					.orElseThrow(() -> new IllegalStateException("AgentExecution " + execution.getId()
							+ " is SUCCEEDED but has no accepted Candidate - acceptance evidence is incomplete"));
		}

		WebsiteImplementationCandidate candidate;
		try {
			candidate = promoter.promote(
					projectId, execution.getId(), developerResultJson, executionInputJson, workspace, snapshot);
		} catch (RuntimeException infrastructureFailure) {
			execution.error("Candidate acceptance failed: " + infrastructureFailure.getMessage());
			agentExecutionRepository.saveAndFlush(execution);
			throw infrastructureFailure;
		}

		execution.succeed();
		agentExecutionRepository.saveAndFlush(execution);
		return candidate;
	}
}
