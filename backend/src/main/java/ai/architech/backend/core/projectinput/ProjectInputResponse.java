package ai.architech.backend.core.projectinput;

import java.time.Instant;
import java.util.UUID;

public record ProjectInputResponse(UUID id, UUID projectId, String content, Instant createdAt) {

	static ProjectInputResponse from(ProjectInput input) {
		return new ProjectInputResponse(
				input.getId(), input.getProjectId(), input.getContent(), input.getCreatedAt());
	}
}
