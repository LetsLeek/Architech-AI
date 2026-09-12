package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiMessage;
import ai.architech.backend.core.ai.AiRequest;
import ai.architech.backend.core.ai.AiResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the deterministic surrounding code (prompt assembly, response parsing, policy
 * mapping, graceful handling of malformed output) against a mocked {@link AiGateway} - see this
 * class's own javadoc for why a real model's judgment quality is deliberately not, and cannot
 * be, asserted here.
 */
@ExtendWith(MockitoExtension.class)
class DesignProposalSetSemanticReviewerTests {

	private static final String DESIGN_PROPOSAL_SET = """
			{"proposals": [{"localRef": "prop-a"}]}
			""";
	private static final String CUSTOMER_PROFILE = """
			{"locations": []}
			""";
	private static final String WEBSITE_REQUIREMENTS = """
			{"goals": []}
			""";

	@Mock
	private AiGateway aiGateway;

	private final SemanticReviewPolicy allBlockingPolicy = new SemanticReviewPolicy(new SemanticReviewProperties(null));

	@Test
	void returnsAPassedResultWhenTheModelReportsNoFindings() {
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, allBlockingPolicy, new ObjectMapper());
		mockAiResponse("""
				{"findings": []}
				""");

		SemanticReviewResult result = reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		assertThat(result.hasBlockingFindings()).isFalse();
		assertThat(result.findings()).isEmpty();
	}

	@Test
	void mapsAReportedFindingToABlockingResultByDefault() {
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, allBlockingPolicy, new ObjectMapper());
		mockAiResponse(
				"""
				{"findings": [
				  {"proposalRef": "prop-a", "category": "unsupported-scope", "message": "invented a booking system"}
				]}
				""");

		SemanticReviewResult result = reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		assertThat(result.hasBlockingFindings()).isTrue();
		assertThat(result.findings()).hasSize(1);
		SemanticReviewFinding finding = result.findings().get(0);
		assertThat(finding.proposalRef()).isEqualTo("prop-a");
		assertThat(finding.category()).isEqualTo("unsupported-scope");
		assertThat(finding.blocking()).isTrue();
	}

	@Test
	void respectsAPolicyOverrideDowngradingASpecificCategoryToNonBlocking() {
		SemanticReviewPolicy policy = new SemanticReviewPolicy(new SemanticReviewProperties(Set.of("insufficient-differentiation")));
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, policy, new ObjectMapper());
		mockAiResponse(
				"""
				{"findings": [
				  {"proposalRef": null, "category": "insufficient-differentiation", "message": "all three proposals share identical navigation"}
				]}
				""");

		SemanticReviewResult result = reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		assertThat(result.hasBlockingFindings()).isFalse();
		assertThat(result.findings().get(0).blocking()).isFalse();
	}

	@Test
	void collectsMultipleFindingsAcrossDifferentProposals() {
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, allBlockingPolicy, new ObjectMapper());
		mockAiResponse(
				"""
				{"findings": [
				  {"proposalRef": "prop-a", "category": "requirement-lost", "message": "missing the contact requirement"},
				  {"proposalRef": "prop-b", "category": "proposal-ranked", "message": "labeled as the recommended option"}
				]}
				""");

		SemanticReviewResult result = reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		assertThat(result.findings()).extracting(SemanticReviewFinding::proposalRef).containsExactly("prop-a", "prop-b");
		assertThat(result.findings()).extracting(SemanticReviewFinding::category).containsExactly("requirement-lost", "proposal-ranked");
	}

	@Test
	void treatsUnparseableModelOutputAsABlockingReviewFailure() {
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, allBlockingPolicy, new ObjectMapper());
		mockAiResponse("not json {{{");

		SemanticReviewResult result = reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		assertThat(result.hasBlockingFindings()).isTrue();
		assertThat(result.findings()).extracting(SemanticReviewFinding::category).containsExactly("review-unavailable");
	}

	@Test
	void treatsAMissingFindingsArrayAsABlockingReviewFailure() {
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, allBlockingPolicy, new ObjectMapper());
		mockAiResponse("{}");

		SemanticReviewResult result = reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		assertThat(result.hasBlockingFindings()).isTrue();
		assertThat(result.findings()).extracting(SemanticReviewFinding::category).containsExactly("review-unavailable");
	}

	@Test
	void treatsTheRealMockProviderEmptyResponseAsABlockingReviewFailureRatherThanThrowing() {
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, allBlockingPolicy, new ObjectMapper());
		mockAiResponse("");

		SemanticReviewResult result = reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		assertThat(result.hasBlockingFindings()).isTrue();
	}

	@Test
	void sendsAllThreeCanonicalJsonDocumentsToTheModelVerbatim() {
		DesignProposalSetSemanticReviewer reviewer = new DesignProposalSetSemanticReviewer(aiGateway, allBlockingPolicy, new ObjectMapper());
		mockAiResponse("""
				{"findings": []}
				""");

		reviewer.review(DESIGN_PROPOSAL_SET, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, "corr-1");

		ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
		verify(aiGateway).invoke(captor.capture());
		AiRequest request = captor.getValue();
		assertThat(request.modelProfile()).isEqualTo(DesignProposalSetSemanticReviewer.MODEL_PROFILE);
		assertThat(request.correlationId()).isEqualTo("corr-1");

		String userMessage = request.messages().stream()
				.filter(message -> "user".equals(message.role()))
				.map(AiMessage::content)
				.findFirst()
				.orElseThrow();
		assertThat(userMessage).contains(DESIGN_PROPOSAL_SET).contains(CUSTOMER_PROFILE).contains(WEBSITE_REQUIREMENTS);
	}

	private void mockAiResponse(String content) {
		when(aiGateway.invoke(any())).thenReturn(new AiResponse("mock", "mock-model", content, "corr-1", null, null, null, null));
	}
}
