package ai.architech.backend.core.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A fixed, point-in-time set of a project's evidence item ids (free-text, structured, and
 * file inputs - all already immutable in their own right). Genuinely immutable: no setters,
 * no update methods at all. Adding a new customer input after a snapshot was taken never
 * changes that snapshot - a later AgentExecution that wants the new evidence needs a new
 * snapshot, not a mutation of this one.
 */
@Entity
@Table(name = "evidence_snapshot")
public class EvidenceSnapshot {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "project_input_ids", nullable = false, updatable = false)
	private List<UUID> projectInputIds;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "structured_project_input_ids", nullable = false, updatable = false)
	private List<UUID> structuredProjectInputIds;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "file_project_input_ids", nullable = false, updatable = false)
	private List<UUID> fileProjectInputIds;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EvidenceSnapshot() {
		// required by JPA
	}

	public EvidenceSnapshot(
			UUID projectId,
			List<UUID> projectInputIds,
			List<UUID> structuredProjectInputIds,
			List<UUID> fileProjectInputIds) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.projectInputIds = List.copyOf(projectInputIds);
		this.structuredProjectInputIds = List.copyOf(structuredProjectInputIds);
		this.fileProjectInputIds = List.copyOf(fileProjectInputIds);
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

	public List<UUID> getProjectInputIds() {
		return projectInputIds;
	}

	public List<UUID> getStructuredProjectInputIds() {
		return structuredProjectInputIds;
	}

	public List<UUID> getFileProjectInputIds() {
		return fileProjectInputIds;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
