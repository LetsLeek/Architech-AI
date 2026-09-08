package ai.architech.backend.core.artifact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One immutable, already-validated version of an {@link Artifact}'s content, produced by
 * exactly one {@code AgentExecution}. Reaching this table at all means validation already
 * passed - a rejected candidate never becomes an ArtifactVersion (see AIW-44). No setters,
 * no update methods: a correction is always a new version, never an edit of this one.
 */
@Entity
@Table(name = "artifact_version")
public class ArtifactVersion {

	@Id
	private UUID id;

	@Column(name = "artifact_id", nullable = false, updatable = false)
	private UUID artifactId;

	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@Column(name = "agent_execution_id", nullable = false, updatable = false)
	private UUID agentExecutionId;

	@Column(name = "content", nullable = false, updatable = false)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ArtifactVersion() {
		// required by JPA
	}

	public ArtifactVersion(UUID artifactId, int versionNumber, UUID agentExecutionId, String content) {
		this.id = UUID.randomUUID();
		this.artifactId = artifactId;
		this.versionNumber = versionNumber;
		this.agentExecutionId = agentExecutionId;
		this.content = content;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getArtifactId() {
		return artifactId;
	}

	public int getVersionNumber() {
		return versionNumber;
	}

	public UUID getAgentExecutionId() {
		return agentExecutionId;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
