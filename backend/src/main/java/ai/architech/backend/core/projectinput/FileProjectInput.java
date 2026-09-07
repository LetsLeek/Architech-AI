package ai.architech.backend.core.projectinput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A file the customer uploaded as evidence for a project. Stores the raw bytes plus origin
 * metadata only - no text extraction happens here, that is a separate, later platform
 * concern (Source Context Builder), not something upload persistence should do. Nothing
 * here gives the Requirements Agent direct file/tool access; the agent only ever sees
 * whatever the platform later derives into its Source Context.
 */
@Entity
@Table(name = "file_project_input")
public class FileProjectInput {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

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

	protected FileProjectInput() {
		// required by JPA
	}

	public FileProjectInput(UUID projectId, String filename, String contentType, byte[] content) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
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

	public String getFilename() {
		return filename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
