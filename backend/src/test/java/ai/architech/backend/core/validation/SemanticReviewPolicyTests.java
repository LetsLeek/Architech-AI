package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SemanticReviewPolicyTests {

	@Test
	void everyCategoryIsBlockingByDefault() {
		SemanticReviewPolicy policy = new SemanticReviewPolicy(new SemanticReviewProperties(null));

		for (String category : DesignProposalSetSemanticReviewer.categories()) {
			assertThat(policy.isBlocking(category)).as(category).isTrue();
		}
	}

	@Test
	void anUnrecognizedCategoryIsBlockingByDefaultAsASafeFallback() {
		SemanticReviewPolicy policy = new SemanticReviewPolicy(new SemanticReviewProperties(null));

		assertThat(policy.isBlocking("some-future-category")).isTrue();
	}

	@Test
	void aCategoryListedAsNonBlockingIsNotBlocking() {
		SemanticReviewPolicy policy = new SemanticReviewPolicy(new SemanticReviewProperties(Set.of("filler-or-throwaway-variant")));

		assertThat(policy.isBlocking("filler-or-throwaway-variant")).isFalse();
		assertThat(policy.isBlocking("unsupported-scope")).isTrue();
	}
}
