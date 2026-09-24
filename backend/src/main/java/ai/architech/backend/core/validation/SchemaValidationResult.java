package ai.architech.backend.core.validation;

import java.util.List;

public record SchemaValidationResult(boolean valid, List<SchemaValidationIssue> issues) {

	public static SchemaValidationResult passed() {
		return new SchemaValidationResult(true, List.of());
	}
}
