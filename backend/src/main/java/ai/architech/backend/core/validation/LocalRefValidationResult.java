package ai.architech.backend.core.validation;

import java.util.List;

public record LocalRefValidationResult(boolean valid, List<LocalRefValidationIssue> issues) {

	public static LocalRefValidationResult passed() {
		return new LocalRefValidationResult(true, List.of());
	}
}
