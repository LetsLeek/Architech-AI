package ai.architech.backend.core.documentation.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One immutable, canonical {@code documentation-package-version.schema.json} payload (AIW-200) -
 * the same "store the real schema-shaped JSON directly, don't just scatter loose columns" idiom
 * {@link ai.architech.backend.core.documentation.context.DocumentationContext} already
 * established for the context it was generated from. No setters, no update methods: a new
 * revision is always a new row (linear revisioning, {@code DECISION_LOG.md} point 11), never a
 * mutation of this one - {@link #supersedesPackageVersionId} is this row's own one-way pointer
 * back to the revision it replaced within the same {@link DocumentationLine}, absent only for a
 * line's very first (revision 1) canonical version.
 *
 * <p>{@code idempotencyKey} is unique per {@code documentationLineId} (a real DB constraint) -
 * {@code validators/package/ASSEMBLY.md} point 5: "Retry commit with identical candidate/
 * operation idempotency key returns the same canonical version even if prior response timed
 * out." {@link DocumentationCanonicalPackagePersister} checks for an existing row with this exact
 * pair before ever assigning a new revision.
 */
@Entity
@Table(name = "documentation_package_version")
public class DocumentationPackageVersion {

	@Id
	private UUID id;

	@Column(name = "documentation_line_id", nullable = false, updatable = false)
	private UUID documentationLineId;

	@Column(name = "revision", nullable = false, updatable = false)
	private int revision;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "context_id", nullable = false, updatable = false)
	private UUID contextId;

	@Column(name = "profile_ref", nullable = false, updatable = false)
	private String profileRef;

	@Column(name = "idempotency_key", nullable = false, updatable = false)
	private String idempotencyKey;

	@Column(name = "supersedes_package_version_id", updatable = false)
	private UUID supersedesPackageVersionId;

	@Column(name = "content_json", nullable = false, updatable = false)
	private String contentJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentationPackageVersion() {
		// required by JPA
	}

	public DocumentationPackageVersion(
			UUID id,
			UUID documentationLineId,
			int revision,
			UUID projectId,
			UUID contextId,
			String profileRef,
			String idempotencyKey,
			UUID supersedesPackageVersionId,
			String contentJson) {
		this.id = Objects.requireNonNull(id);
		this.documentationLineId = Objects.requireNonNull(documentationLineId);
		this.revision = revision;
		this.projectId = Objects.requireNonNull(projectId);
		this.contextId = Objects.requireNonNull(contextId);
		this.profileRef = Objects.requireNonNull(profileRef);
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
		this.supersedesPackageVersionId = supersedesPackageVersionId;
		this.contentJson = Objects.requireNonNull(contentJson);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getDocumentationLineId() {
		return documentationLineId;
	}

	public int getRevision() {
		return revision;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getContextId() {
		return contextId;
	}

	public String getProfileRef() {
		return profileRef;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Optional<UUID> getSupersedesPackageVersionId() {
		return Optional.ofNullable(supersedesPackageVersionId);
	}

	public String getContentJson() {
		return contentJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
