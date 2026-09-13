package ai.architech.backend.core.validation;

import java.util.List;

public record DeveloperResultValidationResult(boolean valid, List<DeveloperResultValidationIssue> issues) {

	public static DeveloperResultValidationResult passed() {
		return new DeveloperResultValidationResult(true, List.of());
	}
}
