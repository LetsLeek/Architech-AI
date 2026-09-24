package ai.architech.backend.core.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The exact, immutable {@code qa-execution-input.v1} payload one {@link QaExecution} was run
 * against (AIW-168) - {@code QaResult.provenance.inputSnapshotRef} always points back to exactly
 * this row's own id, so "what authority was this QA conclusion actually evaluated against" is
 * always reproducible from a stable, never-mutated record, matching {@code rules/target-input-
 * integrity.md}'s "MUST NOT silently load newer authority during an active run".
 */
@Entity
@Table(name = "qa_input_snapshot")
public class QaInputSnapshot {

	@Id
	private UUID id;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "input_json", nullable = false, updatable = false)
	private String inputJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected QaInputSnapshot() {
		// required by JPA
	}

	public QaInputSnapshot(UUID qaExecutionId, String inputJson) {
		this.id = UUID.randomUUID();
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
		this.inputJson = Objects.requireNonNull(inputJson);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getQaExecutionId() {
		return qaExecutionId;
	}

	public String getInputJson() {
		return inputJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
