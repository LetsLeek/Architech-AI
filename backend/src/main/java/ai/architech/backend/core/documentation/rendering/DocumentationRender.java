package ai.architech.backend.core.documentation.rendering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable presentation derivative of a canonical {@code DocumentationPackageVersion}
 * (AIW-201) - matches {@code documentation-render.schema.json}'s envelope, with {@code
 * outputRef} pointing back at this same row's own id (the rendered content lives right here in
 * {@link #content}, not in a separate blob store). No setters, no update methods: re-rendering the
 * same package version creates a new row rather than mutating this one - {@code
 * validators/package/ASSEMBLY.md} point 6, "Re-render alone never changes canonical semantic
 * revision," and this row itself is just as immutable as the canonical data it was derived from.
 *
 * <p>{@code format} is always {@code "MARKDOWN"} in V1 - {@code HTML}/{@code PDF} are real,
 * schema-permitted values this ticket deliberately does not build a renderer for (PDF needs a new
 * library dependency, a separate decision; HTML is a genuinely different templating concern, not
 * just a text-format variant of Markdown).
 */
@Entity
@Table(name = "documentation_render")
public class DocumentationRender {

	@Id
	private UUID id;

	@Column(name = "package_version_id", nullable = false, updatable = false)
	private UUID packageVersionId;

	@Column(name = "format", nullable = false, updatable = false)
	private String format;

	@Column(name = "renderer_version", nullable = false, updatable = false)
	private String rendererVersion;

	@Column(name = "content", nullable = false, updatable = false)
	private String content;

	@Column(name = "content_json", nullable = false, updatable = false)
	private String contentJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentationRender() {
		// required by JPA
	}

	public DocumentationRender(
			UUID id, UUID packageVersionId, String format, String rendererVersion, String content, String contentJson) {
		this.id = Objects.requireNonNull(id);
		this.packageVersionId = Objects.requireNonNull(packageVersionId);
		this.format = Objects.requireNonNull(format);
		this.rendererVersion = Objects.requireNonNull(rendererVersion);
		this.content = Objects.requireNonNull(content);
		this.contentJson = Objects.requireNonNull(contentJson);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getPackageVersionId() {
		return packageVersionId;
	}

	public String getFormat() {
		return format;
	}

	public String getRendererVersion() {
		return rendererVersion;
	}

	public String getContent() {
		return content;
	}

	public String getContentJson() {
		return contentJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
