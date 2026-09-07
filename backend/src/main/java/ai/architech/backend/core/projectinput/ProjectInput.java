package ai.architech.backend.core.projectinput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single piece of free-text customer evidence submitted for a project. Immutable by
 * design: once persisted, a ProjectInput is never updated - a correction is a new
 * ProjectInput, not an edit, so later evidence snapshots can rely on inputs never
 * changing after the fact.
 */
@Entity
@Table(name = "project_input")
public class ProjectInput {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProjectInput() {
		// required by JPA
	}

	public ProjectInput(UUID projectId, String content) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.content = content;
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

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
