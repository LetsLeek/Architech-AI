package ai.architech.backend.core.artifact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The stable identity of "the artifact of this type for this project" - e.g. one Artifact
 * row exists for a project's customer-profile forever, regardless of how many
 * {@link ArtifactVersion}s come and go under it. Generic: {@code type} is a plain string,
 * not a Requirements-specific enum - Core never needs to change to support an artifact type
 * beyond "customer-profile"/"website-requirements".
 */
@Entity
@Table(name = "artifact")
public class Artifact {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "type", nullable = false, updatable = false)
	private String type;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Artifact() {
		// required by JPA
	}

	public Artifact(UUID projectId, String type) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.type = type;
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

	public String getType() {
		return type;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
