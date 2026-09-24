package ai.architech.backend.core.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer's project on the platform. Project-type-agnostic by design: {@code projectType}
 * is a plain string so Core never has to be changed to support a project type beyond
 * {@code website}.
 */
@Entity
@Table(name = "project")
public class Project {

	@Id
	private UUID id;

	@Column(name = "project_type", nullable = false, updatable = false)
	private String projectType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Project() {
		// required by JPA
	}

	public Project(String projectType) {
		this.id = UUID.randomUUID();
		this.projectType = projectType;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getProjectType() {
		return projectType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
