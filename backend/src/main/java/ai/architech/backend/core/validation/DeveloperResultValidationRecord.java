package ai.architech.backend.core.validation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable, auditable record of exactly one {@link DeveloperResultValidator#validate} call
 * (AIW-161), correlated to the owning {@code AgentExecution} by {@link #agentExecutionId} - the
 * same plain-column convention {@code ToolExecution}/{@code RunnerVerificationRun} already use,
 * never a JPA relationship. Deliberately no uniqueness constraint on {@code agentExecutionId}: a
 * bounded self-correction cycle (AIW-150) re-validates a corrected result within the same
 * execution, so one execution can legitimately accumulate several validation records, each its
 * own row, never updated or replaced in place - this is what makes "correlation survives workflow
 * retry lineage without overwriting historical evidence" true for validation evidence specifically,
 * exactly the same reasoning {@code RunnerVerificationRun}'s own javadoc gives for verification.
 *
 * <p>{@code issuesJson} is a bounded JSON array of {@code {validator, ref, reason}} objects - this
 * platform's own validator-authored findings, never raw model output, a tool payload, or anything
 * else that could carry a secret or an unbounded transcript (this ticket's own "avoid storing...
 * secrets or full unbounded raw transcripts" acceptance criterion).
 */
@Entity
@Table(name = "developer_result_validation_record")
public class DeveloperResultValidationRecord {

	@Id
	private UUID id;

	@Column(name = "agent_execution_id", nullable = false, updatable = false)
	private UUID agentExecutionId;

	@Column(name = "valid", nullable = false, updatable = false)
	private boolean valid;

	@Column(name = "issues", nullable = false, updatable = false)
	private String issuesJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DeveloperResultValidationRecord() {
		// required by JPA
	}

	public DeveloperResultValidationRecord(UUID agentExecutionId, boolean valid, String issuesJson) {
		this.id = UUID.randomUUID();
		this.agentExecutionId = Objects.requireNonNull(agentExecutionId);
		this.valid = valid;
		this.issuesJson = Objects.requireNonNull(issuesJson);
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

	public boolean isValid() {
		return valid;
	}

	public String getIssuesJson() {
		return issuesJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
