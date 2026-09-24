package ai.architech.backend.core.documentation.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One logical "Documentation Line" (AIW-200) - project + profile identity + locale (which already
 * determines audience, since each frozen profile declares exactly one {@code primaryAudience}),
 * per {@code validators/package/ASSEMBLY.md} point 4: "For the same logical Documentation Line...
 * serialize revision assignment; each new published revision explicitly supersedes the immediately
 * previous canonical revision. Different locale or audience lines are parallel and never
 * supersede each other."
 *
 * <p>{@code @Version}-based optimistic locking mirrors {@link
 * ai.architech.backend.projecttype.website.ComparisonReadinessSlot}'s own exact idiom: two racing
 * canonicalization attempts on the same line will have the second, stale one fail with a real
 * {@code ObjectOptimisticLockingFailureException} rather than silently double-assigning a
 * revision number - {@link DocumentationCanonicalPackagePersister} is what turns that failure into
 * "serialized" revision assignment, via a bounded retry loop re-reading this row fresh each
 * attempt.
 */
@Entity
@Table(name = "documentation_line")
public class DocumentationLine {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "profile_ref", nullable = false, updatable = false)
	private String profileRef;

	@Column(name = "locale", nullable = false, updatable = false)
	private String locale;

	@Column(name = "current_package_version_id")
	private UUID currentPackageVersionId;

	@Column(name = "current_revision", nullable = false)
	private int currentRevision;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentationLine() {
		// required by JPA
	}

	public DocumentationLine(UUID projectId, String profileRef, String locale) {
		this.id = UUID.randomUUID();
		this.projectId = Objects.requireNonNull(projectId);
		this.profileRef = Objects.requireNonNull(profileRef);
		this.locale = Objects.requireNonNull(locale);
		this.currentRevision = 0;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/** Advances this line's current-canonical pointer - the one mutation this row ever undergoes. */
	public void advance(UUID newPackageVersionId, int newRevision) {
		this.currentPackageVersionId = Objects.requireNonNull(newPackageVersionId);
		this.currentRevision = newRevision;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getProfileRef() {
		return profileRef;
	}

	public String getLocale() {
		return locale;
	}

	public Optional<UUID> getCurrentPackageVersionId() {
		return Optional.ofNullable(currentPackageVersionId);
	}

	public int getCurrentRevision() {
		return currentRevision;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
