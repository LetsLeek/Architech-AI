package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FunctionalBindingValidatorTests {

	private final FunctionalBindingValidator validator = new FunctionalBindingValidator();

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{"functionalRequirements": [{"localRef": "req-func-1"}]}""";

	private static final String PROPOSAL_ADDRESSING_IT =
			"""
			{"websitePlan": {"requirementRefs": ["req-func-1"], "pages": []}}""";

	private static final String PROPOSAL_NOT_ADDRESSING_IT = """
			{"websitePlan": {"pages": []}}""";

	@Test
	void passesWithExactlyOneBindingForTheAddressedRequirement() {
		String result = """
				{"functionalBindings": [{"requirementRef": "req-func-1", "status": "IMPLEMENTED_LOCAL"}]}""";

		assertThat(validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL_ADDRESSING_IT).valid()).isTrue();
	}

	@Test
	void failsWhenAnAddressedRequirementHasNoBindingAtAll() {
		DeveloperResultValidationResult validationResult =
				validator.validate("""
						{"functionalBindings": []}""", WEBSITE_REQUIREMENTS, PROPOSAL_ADDRESSING_IT);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("has no binding"));
	}

	@Test
	void failsWhenTheSameRequirementHasTwoBindings() {
		String result =
				"""
				{"functionalBindings": [
				  {"requirementRef": "req-func-1", "status": "IMPLEMENTED_LOCAL"},
				  {"requirementRef": "req-func-1", "status": "IMPLEMENTED_LOCAL"}
				]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL_ADDRESSING_IT);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("exactly one is required"));
	}

	@Test
	void failsWhenABindingTargetsARequirementTheProposalNeverAddressed() {
		String result = """
				{"functionalBindings": [{"requirementRef": "req-func-1", "status": "IMPLEMENTED_LOCAL"}]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL_NOT_ADDRESSING_IT);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("not a functional requirement this proposal addresses"));
	}

	@Test
	void reportsMalformedJsonAsASingleIssue() {
		assertThat(validator.validate("not json", WEBSITE_REQUIREMENTS, PROPOSAL_ADDRESSING_IT).valid()).isFalse();
	}
}
