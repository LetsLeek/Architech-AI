package ai.architech.backend.core.validation;

import org.springframework.stereotype.Component;

@Component
public class SemanticReviewPolicy {

	private final SemanticReviewProperties properties;

	SemanticReviewPolicy(SemanticReviewProperties properties) {
		this.properties = properties;
	}

	public boolean isBlocking(String category) {
		return !properties.nonBlockingCategories().contains(category);
	}
}
