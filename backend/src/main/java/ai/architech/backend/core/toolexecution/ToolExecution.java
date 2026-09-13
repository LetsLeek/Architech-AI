package ai.architech.backend.core.toolexecution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One auditable record of exactly one Developer tool invocation, correlated to the owning
 * {@code AgentExecution} by {@link #agentExecutionId} (a plain column, not a JPA relationship -
 * matching {@code ArtifactVersion}'s own convention). {@code correctionCycle} distinguishes
 * repeated tool calls made across AIW-150's bounded self-correction cycles *within* one
 * execution; a Workflow-level retry (AIW-149) is a distinct {@code AgentExecution} row
 * entirely, so correlation across retries falls out of {@link #agentExecutionId} alone without
 * needing its own field here.
 *
 * <p>Rows are written already-terminal (a tool call already finished, one way or another, by
 * the time anything constructs one of these) - there are no update methods, matching every
 * other immutable evidence row in this codebase ({@code ArtifactVersion}, {@code ToolExecution}
 * is never corrected in place, only ever superseded by a later row).
 *
 * <p>Nothing in this class, or anywhere else in {@code core.toolexecution}, is read by
 * {@code CandidatePromoter} or embedded into a {@code WebsiteImplementationCandidate} payload -
 * this table exists purely as audit/validation evidence (AIW-148's own acceptance criteria).
 */
@Entity
@Table(name = "tool_execution")
public class ToolExecution {

	@Id
	private UUID id;

	@Column(name = "agent_execution_id", nullable = false, updatable = false)
	private UUID agentExecutionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "capability", nullable = false, updatable = false)
	private ToolCapability capability;

	@Column(name = "tool_name", nullable = false, updatable = false)
	private String toolName;

	@Column(name = "correction_cycle", nullable = false, updatable = false)
	private int correctionCycle;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, updatable = false)
	private ToolExecutionStatus status;

	@Column(name = "diagnostic_summary", updatable = false)
	private String diagnosticSummary;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "finished_at", nullable = false, updatable = false)
	private Instant finishedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ToolExecution() {
		// required by JPA
	}

	public ToolExecution(
			UUID agentExecutionId,
			ToolCapability capability,
			String toolName,
			int correctionCycle,
			ToolExecutionStatus status,
			String rawDiagnostics,
			Instant startedAt,
			Instant finishedAt) {
		if (correctionCycle < 0) {
			throw new IllegalArgumentException("correctionCycle must not be negative: " + correctionCycle);
		}
		if (finishedAt.isBefore(startedAt)) {
			throw new IllegalArgumentException("finishedAt must not be before startedAt");
		}
		this.id = UUID.randomUUID();
		this.agentExecutionId = Objects.requireNonNull(agentExecutionId);
		this.capability = Objects.requireNonNull(capability);
		this.toolName = Objects.requireNonNull(toolName);
		this.correctionCycle = correctionCycle;
		this.status = Objects.requireNonNull(status);
		this.diagnosticSummary = ToolExecutionDiagnostics.sanitize(rawDiagnostics);
		this.startedAt = Objects.requireNonNull(startedAt);
		this.finishedAt = Objects.requireNonNull(finishedAt);
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

	public ToolCapability getCapability() {
		return capability;
	}

	public String getToolName() {
		return toolName;
	}

	public int getCorrectionCycle() {
		return correctionCycle;
	}

	public ToolExecutionStatus getStatus() {
		return status;
	}

	public String getDiagnosticSummary() {
		return diagnosticSummary;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
