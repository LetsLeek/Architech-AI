package ai.architech.backend.core.validation;

import java.util.List;

public record SemanticReviewResult(boolean hasBlockingFindings, List<SemanticReviewFinding> findings) {

	public static SemanticReviewResult passed() {
		return new SemanticReviewResult(false, List.of());
	}
}
