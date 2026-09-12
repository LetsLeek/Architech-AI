package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the real Spring wiring - the "semantic-review" model profile resolves through the
 * real {@code AiGateway}/{@code MockAiProvider} exactly like every other model profile - not
 * just the mocked-{@code AiGateway} unit tests in {@link DesignProposalSetSemanticReviewerTests}.
 */
@SpringBootTest
class DesignProposalSetSemanticReviewerIT {

	@Autowired
	private DesignProposalSetSemanticReviewer reviewer;

	@Test
	void resolvesTheRealSemanticReviewModelProfileAndHandlesTheMockProvidersEmptyResponseGracefully() {
		SemanticReviewResult result = reviewer.review(
				"""
				{"proposals": [{"localRef": "prop-a"}]}
				""",
				"""
				{"locations": []}
				""",
				"""
				{"goals": []}
				""",
				"corr-it-1");

		// MockAiProvider always returns "" (no real model behind it) - the reviewer must fail
		// safe (a reportable, blocking finding) rather than throw.
		assertThat(result.hasBlockingFindings()).isTrue();
		assertThat(result.findings()).extracting(SemanticReviewFinding::category).containsExactly("review-unavailable");
	}
}
