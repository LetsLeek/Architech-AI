package ai.architech.backend.core.evidence;

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
 * The persisted mapping from a platform-generated opaque ref string to the real evidence
 * item it points to, scoped to one evidence snapshot. The ref itself carries no meaning -
 * not priority, not truth, not content - it only exists so an agent can cite evidence
 * without ever seeing (or being able to invent/rename) a real input id. Immutable: an
 * agent never creates or renames these, so nothing here needs to change after creation.
 */
@Entity
@Table(name = "source_ref")
public class SourceRef {

	@Id
	private UUID id;

	@Column(name = "evidence_snapshot_id", nullable = false, updatable = false)
	private UUID evidenceSnapshotId;

	@Column(name = "ref", nullable = false, updatable = false)
	private String ref;

	@Column(name = "source_item_id", nullable = false, updatable = false)
	private UUID sourceItemId;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin", nullable = false, updatable = false)
	private SourceOrigin origin;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SourceRef() {
		// required by JPA
	}

	public SourceRef(UUID evidenceSnapshotId, String ref, UUID sourceItemId, SourceOrigin origin) {
		this.id = UUID.randomUUID();
		this.evidenceSnapshotId = evidenceSnapshotId;
		this.ref = ref;
		this.sourceItemId = sourceItemId;
		this.origin = origin;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEvidenceSnapshotId() {
		return evidenceSnapshotId;
	}

	public String getRef() {
		return ref;
	}

	public UUID getSourceItemId() {
		return sourceItemId;
	}

	public SourceOrigin getOrigin() {
		return origin;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
