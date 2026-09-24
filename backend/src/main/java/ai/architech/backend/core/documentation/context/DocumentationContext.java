package ai.architech.backend.core.documentation.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The immutable, frozen {@code documentation-context.schema.json} payload one Documentation
 * generation attempt is run against (AIW-190) - mirrors {@code QaInputSnapshot}'s own "exact
 * input this run was evaluated against, never mutated" idiom. {@code id} doubles as the payload's
 * own {@code contextId}.
 *
 * <p>{@code contextVersion} is always {@code 1} today: no refresh/re-freeze mechanism exists yet
 * (the frozen workflow policy's own {@code optionalRefreshEvents}, e.g. a post-deployment
 * refresh, is later workflow-trigger work - AIW-202 - not built here). A real multi-version
 * lineage per {@code contextId} is future work, not silently assumed away.
 */
@Entity
@Table(name = "documentation_context")
public class DocumentationContext {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "candidate_id", nullable = false, updatable = false)
	private UUID candidateId;

	@Column(name = "qa_result_id", nullable = false, updatable = false)
	private UUID qaResultId;

	@Column(name = "profile_ref", nullable = false, updatable = false)
	private String profileRef;

	@Column(name = "context_version", nullable = false, updatable = false)
	private int contextVersion;

	@Column(name = "content_json", nullable = false, updatable = false)
	private String contentJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentationContext() {
		// required by JPA
	}

	public DocumentationContext(
			UUID id, UUID projectId, UUID candidateId, UUID qaResultId, String profileRef, int contextVersion, String contentJson) {
		this.id = Objects.requireNonNull(id);
		this.projectId = Objects.requireNonNull(projectId);
		this.candidateId = Objects.requireNonNull(candidateId);
		this.qaResultId = Objects.requireNonNull(qaResultId);
		this.profileRef = Objects.requireNonNull(profileRef);
		this.contextVersion = contextVersion;
		this.contentJson = Objects.requireNonNull(contentJson);
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

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getQaResultId() {
		return qaResultId;
	}

	public String getProfileRef() {
		return profileRef;
	}

	public int getContextVersion() {
		return contextVersion;
	}

	public String getContentJson() {
		return contentJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
