package ai.architech.backend.core.validation;

import java.util.List;

public record SourceRefValidationResult(boolean valid, List<SourceRefValidationIssue> issues) {

	public static SourceRefValidationResult passed() {
		return new SourceRefValidationResult(true, List.of());
	}
}
