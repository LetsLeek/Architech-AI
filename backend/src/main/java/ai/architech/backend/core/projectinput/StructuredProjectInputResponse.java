package ai.architech.backend.core.projectinput;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StructuredProjectInputResponse(
		UUID id, UUID projectId, Map<String, String> fields, Instant createdAt) {

	static StructuredProjectInputResponse from(StructuredProjectInput input) {
		return new StructuredProjectInputResponse(
				input.getId(), input.getProjectId(), input.getFields(), input.getCreatedAt());
	}
}
