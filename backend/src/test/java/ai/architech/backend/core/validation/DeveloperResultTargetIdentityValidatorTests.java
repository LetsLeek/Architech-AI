package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeveloperResultTargetIdentityValidatorTests {

	private final DeveloperResultTargetIdentityValidator validator = new DeveloperResultTargetIdentityValidator();

	private static final String EXECUTION_INPUT =
			"""
			{"targetDesign": {"designArtifactVersionRef": "design-v1", "targetProposalLocalRef": "prop-a"}}""";

	@Test
	void passesWhenBothRefsMatch() {
		String result = """
				{"targetDesign": {"designArtifactVersionRef": "design-v1", "proposalLocalRef": "prop-a"}}""";

		assertThat(validator.validate(result, EXECUTION_INPUT).valid()).isTrue();
	}

	@Test
	void failsOnADesignArtifactVersionMismatch() {
		String result = """
				{"targetDesign": {"designArtifactVersionRef": "design-v2", "proposalLocalRef": "prop-a"}}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, EXECUTION_INPUT);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(
				issue -> assertThat(issue.ref()).isEqualTo("targetDesign.designArtifactVersionRef"));
	}

	@Test
	void failsOnAProposalMismatch() {
		String result = """
				{"targetDesign": {"designArtifactVersionRef": "design-v1", "proposalLocalRef": "prop-b"}}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, EXECUTION_INPUT);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.ref()).isEqualTo("targetDesign.proposalLocalRef"));
	}

	@Test
	void reportsBothMismatchesAtOnce() {
		String result = """
				{"targetDesign": {"designArtifactVersionRef": "design-v2", "proposalLocalRef": "prop-b"}}""";

		assertThat(validator.validate(result, EXECUTION_INPUT).issues()).hasSize(2);
	}

	@Test
	void reportsMalformedJsonAsASingleIssue() {
		DeveloperResultValidationResult validationResult = validator.validate("not json", EXECUTION_INPUT);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).singleElement().satisfies(issue -> assertThat(issue.validator()).isEqualTo("target-identity"));
	}
}
