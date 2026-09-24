package ai.architech.backend.projecttype.website;

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
 * One A/B/C initial-generation fan-out attempt for a project's canonical {@code
 * design-proposal-set} (AIW-146) - the aggregate root grouping exactly three {@link
 * InitialGenerationSlot}s, one per proposal. {@code designProposalSetArtifactVersionId} pins the
 * exact set every sibling was spawned from, so a later re-generation of the design set (a new
 * {@code ArtifactVersion}) never silently gets attributed to an old batch.
 *
 * <p>Unlike the immutable evidence rows elsewhere in this codebase ({@code ToolExecution}, {@code
 * RunnerVerificationRun}), this is live orchestration state - {@link #complete()}/{@link
 * #escalate} intentionally mutate {@code status} in place, the same way {@code AgentExecution}
 * itself is a mutable state machine rather than an append-only log.
 */
@Entity
@Table(name = "initial_generation_batch")
public class InitialGenerationBatch {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "design_proposal_set_artifact_version_id", nullable = false, updatable = false)
	private UUID designProposalSetArtifactVersionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private InitialGenerationBatchStatus status;

	@Column(name = "escalation_reason")
	private String escalationReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected InitialGenerationBatch() {
		// required by JPA
	}

	public InitialGenerationBatch(UUID projectId, UUID designProposalSetArtifactVersionId) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.designProposalSetArtifactVersionId = designProposalSetArtifactVersionId;
		this.status = InitialGenerationBatchStatus.IN_PROGRESS;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/** All three slots currently hold a SUCCEEDED execution with its own accepted Candidate. */
	public void complete() {
		requireInProgress("complete");
		this.status = InitialGenerationBatchStatus.COMPLETE;
	}

	/** A sibling exhausted its retry budget, or was semantically BLOCKED - mechanical progress stops here. */
	public void escalate(String reason) {
		requireInProgress("escalate");
		this.status = InitialGenerationBatchStatus.ESCALATED;
		this.escalationReason = reason;
	}

	private void requireInProgress(String action) {
		if (status != InitialGenerationBatchStatus.IN_PROGRESS) {
			throw new IllegalStateException("Cannot " + action + " a batch that is already " + status);
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getDesignProposalSetArtifactVersionId() {
		return designProposalSetArtifactVersionId;
	}

	public InitialGenerationBatchStatus getStatus() {
		return status;
	}

	public String getEscalationReason() {
		return escalationReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
