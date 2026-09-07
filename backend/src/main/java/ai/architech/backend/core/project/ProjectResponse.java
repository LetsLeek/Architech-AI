package ai.architech.backend.core.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, String projectType, Instant createdAt, Instant updatedAt) {

	static ProjectResponse from(Project project) {
		return new ProjectResponse(
				project.getId(), project.getProjectType(), project.getCreatedAt(), project.getUpdatedAt());
	}
}
