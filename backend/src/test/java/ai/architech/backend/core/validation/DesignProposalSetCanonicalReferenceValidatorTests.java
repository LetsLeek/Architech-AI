package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DesignProposalSetCanonicalReferenceValidatorTests {

	private final DesignProposalSetCanonicalReferenceValidator validator = new DesignProposalSetCanonicalReferenceValidator();

	private static final String CUSTOMER_PROFILE = """
			{"business": {"name": "Green Leaf Cafe"}, "locations": [{"localRef": "cust-1"}]}
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
	void acceptsACustomerDataRefThatIsADotPathToAPresentScalarCustomerProfileField() {
		// "business.name" has no localRef of its own (only array items do) - a candidate citing a
		// top-level scalar field legitimately has no localRef to use, so it cites the dot-path
		// instead, and that must resolve against the real customer-profile JSON.
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [
				     {"localRef": "page-1", "sections": [{"localRef": "sec-1", "customerDataRefs": ["business.name"], "elements": []}]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsACustomerDataRefThatIsADotPathToAFieldThatDoesNotExistInTheCustomerProfile() {
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [
				     {"localRef": "page-1", "sections": [{"localRef": "sec-1", "customerDataRefs": ["business.nonexistentField"], "elements": []}]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues())
				.anyMatch(issue -> issue.reason().contains("'business.nonexistentField' is not part of the active customer-profile"));
	}

	@Test
	void reportsUnparseableCandidateJsonAsAFailureRatherThanThrowing() {
		DesignProposalSetValidationResult result = validator.validate("not json {{{", CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	@Test
	void rejectsACustomerDataRefThatIsADotPathToAFieldThatIsPresentButExplicitlyNull() {
		// AIW-222: a realistic customer-profile shape (per NullOptionalFieldNormalizer's own
		// rationale, a model can emit an explicit JSON null for an absent optional field) must not
		// be treated as "real, present" just because the key itself exists.
		String customerProfileWithExplicitNullField =
				"""
				{"business": {"name": "Green Leaf Cafe"}, "contact": {"fax": null}, "locations": [{"localRef": "cust-1"}]}
				""";
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [
				     {"localRef": "page-1", "sections": [{"localRef": "sec-1", "customerDataRefs": ["contact.fax"], "elements": []}]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result =
				validator.validate(designProposalSet, customerProfileWithExplicitNullField, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("'contact.fax' is not part of the active customer-profile"));
	}

	@Test
	void rejectsACustomerDataRefThatIsADotPathToAWholeContainerRatherThanAScalar() {
		// "locations" is present, but it's the whole array - not a single deterministically-knowable
		// fragment the way a specific "locations[].localRef" array item is.
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [
				     {"localRef": "page-1", "sections": [{"localRef": "sec-1", "customerDataRefs": ["locations"], "elements": []}]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("'locations' is not part of the active customer-profile"));
	}

	@Test
	void rejectsACustomerDataRefThatIsADotPathWithATrailingDot() {
		// A naive `split("\\.")` (default limit 0) silently drops a trailing empty segment,
		// treating "business." identically to "business" instead of rejecting the malformed path.
		String designProposalSet =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [
				     {"localRef": "page-1", "sections": [{"localRef": "sec-1", "customerDataRefs": ["business."], "elements": []}]}
				  ]}}
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(designProposalSet, CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("'business.' is not part of the active customer-profile"));
	}
}
