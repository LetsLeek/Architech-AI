package ai.architech.backend.core.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One project-scoped, deterministically resolvable asset (an image, a logo, ...) that a
 * canonical artifact's own content refers to by {@code assetRef} - mirrors
 * {@link ai.architech.backend.core.projectinput.FileProjectInput}'s own raw-content-storage
 * shape (mirrored, not reused, to keep upload-evidence and resolvable-project-assets as two
 * independent lifecycles). {@code assetRef} is unique per project, never globally, so a lookup
 * is always scoped by (project, ref) together - there is no method anywhere that resolves an
 * asset by ref alone.
 */
@Entity
@Table(name = "project_asset")
public class ProjectAsset {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "asset_ref", nullable = false, updatable = false)
	private String assetRef;

	@Column(name = "filename", nullable = false, updatable = false)
	private String filename;

	@Column(name = "content_type", updatable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@Column(name = "content", nullable = false, updatable = false)
	private byte[] content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProjectAsset() {
		// required by JPA
	}

	public ProjectAsset(UUID projectId, String assetRef, String filename, String contentType, byte[] content) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.assetRef = assetRef;
		this.filename = filename;
		this.contentType = contentType;
		this.content = content;
		this.sizeBytes = content.length;
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

	public String getAssetRef() {
		return assetRef;
	}

	public String getFilename() {
		return filename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public byte[] getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
