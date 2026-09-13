package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeveloperResultReferenceValidatorTests {

	private final DeveloperResultReferenceValidator validator = new DeveloperResultReferenceValidator();

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{"functionalRequirements": [{"localRef": "req-func-1"}]}""";

	private static final String PROPOSAL = """
			{"localRef": "prop-a", "websitePlan": {"pages": [{"localRef": "page-a-home"}]}}""";

	@Test
	void passesWhenEveryReferenceIsKnown() {
		String result =
				"""
				{"functionalBindings": [{"requirementRef": "req-func-1", "designLocalRefs": ["page-a-home"]}],
				 "unresolvedIssues": [], "blockers": []}""";

		assertThat(validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL).valid()).isTrue();
	}

	@Test
	void failsOnAForeignRequirementRefInAFunctionalBinding() {
		String result = """
				{"functionalBindings": [{"requirementRef": "req-unknown", "designLocalRefs": ["page-a-home"]}]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(
				issue -> assertThat(issue.ref()).isEqualTo("functionalBindings.requirementRef"));
	}

	@Test
	void failsOnAForeignDesignLocalRefInAFunctionalBinding() {
		String result = """
				{"functionalBindings": [{"requirementRef": "req-func-1", "designLocalRefs": ["page-does-not-exist"]}]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(
				issue -> assertThat(issue.ref()).isEqualTo("functionalBindings.designLocalRefs"));
	}

	@Test
	void failsOnAForeignRefInAnUnresolvedIssue() {
		String result = """
				{"unresolvedIssues": [{"relatedRequirementRefs": ["req-unknown"], "relatedDesignLocalRefs": []}]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(
				issue -> assertThat(issue.ref()).isEqualTo("unresolvedIssues.relatedRequirementRefs"));
	}

	@Test
	void failsOnAForeignRefInABlocker() {
		String result = """
				{"blockers": [{"relatedRequirementRefs": [], "relatedDesignLocalRefs": ["page-does-not-exist"]}]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, WEBSITE_REQUIREMENTS, PROPOSAL);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.ref()).isEqualTo("blockers.relatedDesignLocalRefs"));
	}

	@Test
	void reportsMalformedJsonAsASingleIssue() {
		assertThat(validator.validate("not json", WEBSITE_REQUIREMENTS, PROPOSAL).valid()).isFalse();
	}
}
