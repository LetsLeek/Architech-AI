package ai.architech.backend.core.validation;

import java.util.List;

public record CrossArtifactValidationResult(boolean valid, List<CrossArtifactValidationIssue> issues) {

	public static CrossArtifactValidationResult passed() {
		return new CrossArtifactValidationResult(true, List.of());
	}
}
