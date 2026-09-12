package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DesignProposalSetCanonicalReferenceValidatorTests {

	private final DesignProposalSetCanonicalReferenceValidator validator = new DesignProposalSetCanonicalReferenceValidator();

	private static final String CUSTOMER_PROFILE = """
			{"locations": [{"localRef": "cust-1"}]}
			""";
	private static final String WEBSITE_REQUIREMENTS = """
			{"goals": [{"localRef": "req-1"}]}
			""";

	@Test
	void acceptsRequirementAndCustomerDataRefsThatExistInTheActiveArtifacts() {
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"requirementRefs": ["req-1"], "pages": [
				     {"localRef": "page-1", "requirementRefs": ["req-1"], "sections": [
				       {"localRef": "sec-1", "customerDataRefs": ["cust-1"], "elements": []}
				     ]}
				   ]}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsARequirementRefNotPresentInTheActiveWebsiteRequirementsArtifact() {
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"requirementRefs": ["does-not-exist"], "pages": []}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("'does-not-exist' is not part of the active website-requirements"));
	}

	@Test
	void rejectsACustomerDataRefNotPresentInTheActiveCustomerProfileArtifact() {
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [
				     {"localRef": "page-1", "sections": [{"localRef": "sec-1", "customerDataRefs": ["does-not-exist"], "elements": []}]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("'does-not-exist' is not part of the active customer-profile"));
	}

	@Test
	void rejectsARequirementRefThatIsActuallyAValidCustomerProfileLocalRefInsteadOfARequirementsOne() {
		// "cust-1" is a real localRef, but only in customer-profile - citing it as a requirementRef
		// must still fail, proving the two permitted sets are checked separately rather than pooled.
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"requirementRefs": ["cust-1"], "pages": []}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("'cust-1' is not part of the active website-requirements"));
	}

	@Test
	void acceptsANavigationTargetsSingularRequirementRefAndCustomerDataRef() {
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"navigationGroups": [
				    {"localRef": "nav-1", "items": [
				      {"label": "Goal", "target": {"type": "requirement", "requirementRef": "req-1"}},
				      {"label": "Fact", "target": {"type": "customer-data", "customerDataRef": "cust-1"}}
				    ]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isTrue();
	}

	@Test
	void rejectsADanglingSingularRequirementRefInsideANavigationTarget() {
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"navigationGroups": [
				    {"localRef": "nav-1", "items": [
				      {"label": "Goal", "target": {"type": "requirement", "requirementRef": "does-not-exist"}}
				    ]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("'does-not-exist' is not part of the active website-requirements"));
	}

	@Test
	void reportsUnparseableCandidateJsonAsAFailureRatherThanThrowing() {
		DesignProposalSetValidationResult result = validator.validate("not json {{{", CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}
}
