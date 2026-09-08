package ai.architech.backend.core.validation;

import java.util.List;

public record WebsiteRequirementsValidationResult(boolean valid, List<WebsiteRequirementsValidationIssue> issues) {

	public static WebsiteRequirementsValidationResult passed() {
		return new WebsiteRequirementsValidationResult(true, List.of());
	}
}
