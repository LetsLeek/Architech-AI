package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One proposal-scoped slot within an {@link InitialGenerationBatch} (AIW-146) - exactly one per
 * proposal in the design set, identified by {@code proposalLocalRef}. {@code
 * currentAgentExecutionId} is this slot's one mutable pointer: it always names the single {@link
 * AgentExecution} currently representing this slot's attempt, which is the frozen-terminal
 * execution most recently retried from once a retry happens (never both at once) - this is what
 * makes "existing successful sibling Candidates remain valid while the failed sibling is
 * retried" true for the *other* slots by construction (their own pointer is simply never
 * touched), while still leaving every prior {@code AgentExecution} row for *this* slot
 * permanently intact and traceable (AIW-149's own "a retry is always a new AgentExecution, never
 * a mutation of a previous one").
 *
 * <p>{@link #retry} is the same bounded-increment idiom {@code AgentExecution#authorizeCorrectionCycle}
 * already establishes: it either advances the pointer and returns {@code true}, or changes
 * nothing and returns {@code false} once {@code retriesUsed} has reached the caller's budget -
 * this is what keeps "bounded retry/escalation without creating unbounded loops" structural
 * rather than a discipline the caller has to remember.
 */
@Entity
@Table(name = "initial_generation_slot")
public class InitialGenerationSlot {

	@Id
	private UUID id;

	@Column(name = "batch_id", nullable = false, updatable = false)
	private UUID batchId;

	@Column(name = "proposal_local_ref", nullable = false, updatable = false)
	private String proposalLocalRef;

	@Column(name = "current_agent_execution_id", nullable = false)
	private UUID currentAgentExecutionId;

	@Column(name = "retries_used", nullable = false)
	private int retriesUsed;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected InitialGenerationSlot() {
		// required by JPA
	}

	public InitialGenerationSlot(UUID batchId, String proposalLocalRef, UUID currentAgentExecutionId) {
		this.id = UUID.randomUUID();
		this.batchId = batchId;
		this.proposalLocalRef = Objects.requireNonNull(proposalLocalRef);
		this.currentAgentExecutionId = Objects.requireNonNull(currentAgentExecutionId);
		this.retriesUsed = 0;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/** Read-only capacity check - lets a caller decide whether to escalate before creating a new execution row at all. */
	public boolean hasRetriesRemaining(int maxRetries) {
		return retriesUsed < maxRetries;
	}

	/**
	 * Attempts to advance this slot to a new retry attempt. Returns {@code true} and moves the
	 * pointer to {@code newAgentExecutionId} if the retry budget is not yet exhausted; returns
	 * {@code false}, changing nothing, once {@code retriesUsed} has already reached {@code
	 * maxRetries} - the caller is then expected to escalate the owning batch instead.
	 */
	public boolean retry(UUID newAgentExecutionId, int maxRetries) {
		if (retriesUsed >= maxRetries) {
			return false;
		}
		retriesUsed++;
		currentAgentExecutionId = Objects.requireNonNull(newAgentExecutionId);
		return true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public String getProposalLocalRef() {
		return proposalLocalRef;
	}

	public UUID getCurrentAgentExecutionId() {
		return currentAgentExecutionId;
	}

	public int getRetriesUsed() {
		return retriesUsed;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
