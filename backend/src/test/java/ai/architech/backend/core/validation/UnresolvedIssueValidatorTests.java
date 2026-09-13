package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnresolvedIssueValidatorTests {

	private final UnresolvedIssueValidator validator = new UnresolvedIssueValidator();

	@Test
	void passesWithNoUnresolvedIssues() {
		assertThat(validator.validate("""
				{"unresolvedIssues": []}""").valid()).isTrue();
	}

	@Test
	void passesWithDistinctIssues() {
		String result =
				"""
				{"unresolvedIssues": [
				  {"code": "MISSING_UPSTREAM_INFORMATION", "relatedRequirementRefs": ["req-1"], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "summary": "a"},
				  {"code": "UPSTREAM_CONFLICT", "relatedRequirementRefs": ["req-1"], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "summary": "b"}
				]}""";

		assertThat(validator.validate(result).valid()).isTrue();
	}

	@Test
	void failsOnTwoNearDuplicateIssuesWithDifferentWording() {
		String result =
				"""
				{"unresolvedIssues": [
				  {"code": "MISSING_UPSTREAM_INFORMATION", "relatedRequirementRefs": ["req-1"], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "summary": "first wording"},
				  {"code": "MISSING_UPSTREAM_INFORMATION", "relatedRequirementRefs": ["req-1"], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "summary": "second wording"}
				]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.validator()).isEqualTo("unresolved-issue"));
	}

	@Test
	void reportsMalformedJsonAsASingleIssue() {
		assertThat(validator.validate("not json").valid()).isFalse();
	}
}
