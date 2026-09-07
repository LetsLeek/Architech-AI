package ai.architech.backend.core.projectinput;

import java.time.Instant;
import java.util.UUID;

/** Deliberately excludes the raw file bytes - this describes a file, it doesn't serve it. */
public record FileProjectInputResponse(
		UUID id, UUID projectId, String filename, String contentType, long sizeBytes, Instant createdAt) {

	static FileProjectInputResponse from(FileProjectInput input) {
		return new FileProjectInputResponse(
				input.getId(),
				input.getProjectId(),
				input.getFilename(),
				input.getContentType(),
				input.getSizeBytes(),
				input.getCreatedAt());
	}
}
