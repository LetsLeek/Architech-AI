package ai.architech.backend.core.qa.remediation;

import ai.architech.backend.core.qa.profiles.QaProfileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The Developer-remediation-cycle budget for exactly one (project, stage, Variant Lineage) tuple
 * (AIW-181) - deliberately keyed by the Variant Lineage/stage identity, never by a Candidate id,
 * so that "New Candidate versions do not reset the active stage remediation budget" holds by
 * construction: a remediation cycle always produces a brand-new Candidate (AIW-145's own
 * immutability), but this row's own identity never changes when that happens. {@code
 * variantLineageRef} is meaningful for {@link QaProfileType#COMPARISON_READINESS} (one budget per
 * A/B/C lineage); {@link QaProfileType#FULL_RELEASE} has exactly one release-path Candidate, so
 * {@link #RELEASE_PATH_LINEAGE_REF} is used as a fixed sentinel there instead of a real proposal
 * ref - "Comparison and Full Release may use separate stage budgets" is what the {@code stage}
 * column is for.
 *
 * <p>Deliberately distinct from the platform's own generic {@code
 * architech.runner.max-attempts} QA-execution-retry budget ({@code BoundedRetryAgentRunner}) -
 * "QA execution retries do not consume Developer remediation budget" holds because these are two
 * entirely separate counters, never a shared one.
 *
 * <p>{@link #useRemediationCycle} is the same bounded-increment idiom {@code
 * InitialGenerationSlot#retry} already establishes: either the counter advances and this returns
 * {@code true}, or the budget is already exhausted and this returns {@code false}, changing
 * nothing - "Budget exhaustion never downgrades Findings or creates PASS; it routes to
 * escalation" is exactly what a caller does with a {@code false} result ({@link
 * QaEscalationRouter}), never silently retried past this method's own answer.
 *
 * <p>{@code @Version}-based optimistic locking protects this counter the same way it protects
 * {@code ComparisonReadinessSlot}'s own current-Candidate pointer (also retrofitted in this
 * ticket) - "Late/stale concurrent results cannot overwrite newer workflow pointers without state
 * validation": two concurrent remediation attempts racing to consume the same budget will have
 * one of them fail with a real {@code ObjectOptimisticLockingFailureException} rather than
 * silently losing an update.
 */
@Entity
@Table(name = "qa_remediation_budget")
public class QaRemediationBudget {

	public static final String RELEASE_PATH_LINEAGE_REF = "RELEASE_PATH";

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "variant_lineage_ref", nullable = false, updatable = false)
	private String variantLineageRef;

	@Enumerated(EnumType.STRING)
	@Column(name = "stage", nullable = false, updatable = false)
	private QaProfileType stage;

	@Column(name = "remediation_cycles_used", nullable = false)
	private int remediationCyclesUsed;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected QaRemediationBudget() {
		// required by JPA
	}

	public QaRemediationBudget(UUID projectId, String variantLineageRef, QaProfileType stage) {
		this.id = UUID.randomUUID();
		this.projectId = Objects.requireNonNull(projectId);
		this.variantLineageRef = Objects.requireNonNull(variantLineageRef);
		this.stage = Objects.requireNonNull(stage);
		this.remediationCyclesUsed = 0;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public boolean hasRemediationCyclesRemaining(int maxRemediationCycles) {
		return remediationCyclesUsed < maxRemediationCycles;
	}

	public boolean useRemediationCycle(int maxRemediationCycles) {
		if (remediationCyclesUsed >= maxRemediationCycles) {
			return false;
		}
		remediationCyclesUsed++;
		return true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getVariantLineageRef() {
		return variantLineageRef;
	}

	public QaProfileType getStage() {
		return stage;
	}

	public int getRemediationCyclesUsed() {
		return remediationCyclesUsed;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
