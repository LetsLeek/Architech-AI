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
 * One Variant-Lineage-scoped slot within a {@link ComparisonReadinessBarrier} (AIW-177) - exactly
 * one per required Variant Lineage, identified by {@code variantLineageRef} (the same {@code
 * proposalLocalRef} identity {@code WebsiteImplementationCandidate}/{@code
 * InitialGenerationSlot} already use for "A"/"B"/"C"). {@code currentCandidateId} is this slot's
 * one mutable pointer - unlike {@code InitialGenerationSlot#currentAgentExecutionId} (which
 * points at an {@code AgentExecution} retry), this points at a {@code
 * WebsiteImplementationCandidate}, since a remediation cycle produces an entirely new immutable
 * Candidate rather than retrying the same execution. {@link #updateCurrentCandidate} only ever
 * moves the pointer - it never mutates the Candidate row itself, matching "Workflow maintains
 * current comparison Candidate pointers per Variant Lineage without mutating Candidate history."
 *
 * <p>The slot itself is never deleted, even for a currently-ineligible variant - {@code
 * comparisonGroupPolicy.silentVariantRemovalAllowed: false} in both frozen profiles' own workflow
 * policy is exactly this: "A failed/unready variant cannot be silently removed from a
 * three-variant comparison."
 */
@Entity
@Table(name = "comparison_readiness_slot")
public class ComparisonReadinessSlot {

	@Id
	private UUID id;

	@Column(name = "barrier_id", nullable = false, updatable = false)
	private UUID barrierId;

	@Column(name = "variant_lineage_ref", nullable = false, updatable = false)
	private String variantLineageRef;

	@Column(name = "current_candidate_id", nullable = false)
	private UUID currentCandidateId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ComparisonReadinessSlot() {
		// required by JPA
	}

	public ComparisonReadinessSlot(UUID barrierId, String variantLineageRef, UUID currentCandidateId) {
		this.id = UUID.randomUUID();
		this.barrierId = Objects.requireNonNull(barrierId);
		this.variantLineageRef = Objects.requireNonNull(variantLineageRef);
		this.currentCandidateId = Objects.requireNonNull(currentCandidateId);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/** Advances this slot's pointer to a newly accepted, remediated Candidate for the same Variant Lineage - never touches any prior Candidate row. */
	public void updateCurrentCandidate(UUID newCandidateId) {
		this.currentCandidateId = Objects.requireNonNull(newCandidateId);
	}

	public UUID getId() {
		return id;
	}

	public UUID getBarrierId() {
		return barrierId;
	}

	public String getVariantLineageRef() {
		return variantLineageRef;
	}

	public UUID getCurrentCandidateId() {
		return currentCandidateId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
