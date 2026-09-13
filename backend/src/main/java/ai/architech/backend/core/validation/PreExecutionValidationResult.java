package ai.architech.backend.core.validation;

import java.util.List;

public record PreExecutionValidationResult(List<PreExecutionValidationIssue> issues) {

	public static PreExecutionValidationResult passed() {
		return new PreExecutionValidationResult(List.of());
	}

	public boolean valid() {
		return issues.isEmpty();
	}
}
