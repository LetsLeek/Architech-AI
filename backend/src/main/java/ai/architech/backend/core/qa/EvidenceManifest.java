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
 * The immutable identity one {@link QaExecution}'s collected Evidence is bound to (AIW-168) -
 * {@code QaResult.evidenceManifestRef} always points back to exactly this row's own id. Carries
 * no evidence content itself yet: this is the minimal identity AIW-168's own persistence-model
 * scope calls for ("EvidenceManifest references and provenance fields"); the actual immutable
 * {@code EvidenceRecord} rows this manifest indexes, and the binding/reuse rules around them, are
 * AIW-170's own scope.
 */
@Entity
@Table(name = "evidence_manifest")
public class EvidenceManifest {

	@Id
	private UUID id;

	@Column(name = "qa_execution_id", nullable = false, updatable = false)
	private UUID qaExecutionId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EvidenceManifest() {
		// required by JPA
	}

	public EvidenceManifest(UUID qaExecutionId) {
		this.id = UUID.randomUUID();
		this.qaExecutionId = Objects.requireNonNull(qaExecutionId);
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

	public Instant getCreatedAt() {
		return createdAt;
	}
}
