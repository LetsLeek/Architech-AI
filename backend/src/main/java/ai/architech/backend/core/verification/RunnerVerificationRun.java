package ai.architech.backend.core.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One authoritative Runner Verification invocation (AIW-155), bound to the exact
 * {@code AgentExecution} attempt and frozen handoff state ({@code repositoryStateRef} - the same
 * {@code FrozenHandoffSnapshot#snapshotId()} {@code WebsiteImplementationCandidate} uses, AIW-145)
 * it evaluated. Unlike {@code WebsiteImplementationCandidate}, there is deliberately no
 * uniqueness constraint on {@code agentExecutionId}: a bounded correction cycle (AIW-150) re-runs
 * verification against a new handoff state without starting a new {@code AgentExecution}, so one
 * execution can legitimately have several verification runs - each its own immutable row, never
 * updated in place.
 *
 * <p>A {@code WebsiteImplementationCandidate} references this evidence only through the platform
 * metadata both rows already share ({@code agentExecutionId}) - never by embedding gate logs into
 * the Candidate's own payload, per this ticket's own acceptance criteria.
 */
@Entity
@Table(name = "runner_verification_run")
public class RunnerVerificationRun {

	@Id
	private UUID id;

	@Column(name = "agent_execution_id", nullable = false, updatable = false)
	private UUID agentExecutionId;

	@Column(name = "repository_state_ref", nullable = false, updatable = false)
	private String repositoryStateRef;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, updatable = false)
	private VerificationOutcome outcome;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RunnerVerificationRun() {
		// required by JPA
	}

	public RunnerVerificationRun(UUID agentExecutionId, String repositoryStateRef, VerificationOutcome outcome) {
		this.id = UUID.randomUUID();
		this.agentExecutionId = agentExecutionId;
		this.repositoryStateRef = repositoryStateRef;
		this.outcome = outcome;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getAgentExecutionId() {
		return agentExecutionId;
	}

	public String getRepositoryStateRef() {
		return repositoryStateRef;
	}

	public VerificationOutcome getOutcome() {
		return outcome;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
