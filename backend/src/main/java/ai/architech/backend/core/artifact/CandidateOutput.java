package ai.architech.backend.core.artifact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Raw model output for one artifact type, exactly as produced by one AgentExecution -
 * before anything has checked whether it is any good. Immutable and permanent: an invalid
 * candidate stays here as audit history forever, it is never deleted, edited, or "fixed" in
 * place. The only way for its content to reach a canonical {@link ArtifactVersion} is
 * through {@link CandidatePromoter}, which requires an actual persisted CandidateOutput -
 * arbitrary text can never be promoted directly.
 */
@Entity
@Table(name = "candidate_output")
public class CandidateOutput {

	@Id
	private UUID id;

	@Column(name = "agent_execution_id", nullable = false, updatable = false)
	private UUID agentExecutionId;

	@Column(name = "artifact_type", nullable = false, updatable = false)
	private String artifactType;

	@Column(name = "content", nullable = false, updatable = false)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CandidateOutput() {
		// required by JPA
	}

	public CandidateOutput(UUID agentExecutionId, String artifactType, String content) {
		this.id = UUID.randomUUID();
		this.agentExecutionId = agentExecutionId;
		this.artifactType = artifactType;
		this.content = content;
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

	public String getArtifactType() {
		return artifactType;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
