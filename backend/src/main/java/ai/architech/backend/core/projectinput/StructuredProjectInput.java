package ai.architech.backend.core.projectinput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A structured (form-style) customer submission: raw field name -> raw value, stored
 * exactly as submitted. These are still unverified customer-provided values, not facts -
 * turning them into facts is a later, semantic concern (Requirements Agent / Source
 * Context), not something persistence decides. Immutable, same as {@link ProjectInput}.
 */
@Entity
@Table(name = "structured_project_input")
public class StructuredProjectInput {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "fields", nullable = false, updatable = false)
	private Map<String, String> fields;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StructuredProjectInput() {
		// required by JPA
	}

	public StructuredProjectInput(UUID projectId, Map<String, String> fields) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.fields = fields;
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

	public Map<String, String> getFields() {
		return fields;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
