package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreExecutionValidationResultTests {

	@Test
	void passedHasNoIssuesAndIsValid() {
		PreExecutionValidationResult result = PreExecutionValidationResult.passed();

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void aResultWithAnyIssueIsNotValid() {
		PreExecutionValidationResult result =
				new PreExecutionValidationResult(List.of(new PreExecutionValidationIssue("path", "message")));

		assertThat(result.valid()).isFalse();
	}
}
