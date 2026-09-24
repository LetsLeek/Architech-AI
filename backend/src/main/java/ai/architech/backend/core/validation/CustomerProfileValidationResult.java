package ai.architech.backend.core.validation;

import java.util.List;

public record CustomerProfileValidationResult(boolean valid, List<CustomerProfileValidationIssue> issues) {

	public static CustomerProfileValidationResult passed() {
		return new CustomerProfileValidationResult(true, List.of());
	}
}
