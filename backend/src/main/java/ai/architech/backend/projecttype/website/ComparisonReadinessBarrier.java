package ai.architech.backend.projecttype.website;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The aggregate root grouping exactly three {@link ComparisonReadinessSlot}s - one per required
 * Variant Lineage - a project's own comparison group is evaluated against (AIW-177). Mirrors
 * {@code InitialGenerationBatch}/{@code InitialGenerationSlot}'s own aggregate-root/slot shape,
 * but deliberately not the same classes: this barrier carries no mutable orchestration state of
 * its own (no {@code status}, no {@code escalate}) - readiness is always computed fresh from each
 * slot's current Candidate pointer and that Candidate's own QA Results, never cached, which is
 * what makes "Stale Comparison results cannot qualify newer Candidates" true by construction
 * rather than by discipline (see {@link ComparisonReadinessBarrierEvaluator}).
 */
@Entity
@Table(name = "comparison_readiness_barrier")
public class ComparisonReadinessBarrier {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ComparisonReadinessBarrier() {
		// required by JPA
	}

	public ComparisonReadinessBarrier(UUID projectId) {
		this.id = UUID.randomUUID();
		this.projectId = Objects.requireNonNull(projectId);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
