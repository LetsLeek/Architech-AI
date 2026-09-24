package ai.architech.backend.core.validation;

import java.util.List;

public record DocumentationCandidateValidationResult(boolean valid, List<DocumentationCandidateValidationIssue> issues) {

	public static DocumentationCandidateValidationResult passed() {
		return new DocumentationCandidateValidationResult(true, List.of());
	}
}
