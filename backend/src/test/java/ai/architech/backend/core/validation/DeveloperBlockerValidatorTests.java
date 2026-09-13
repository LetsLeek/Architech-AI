package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.toolexecution.ToolCapability;
import ai.architech.backend.core.toolexecution.ToolExecution;
import ai.architech.backend.core.toolexecution.ToolExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeveloperBlockerValidatorTests {

	private final DeveloperBlockerValidator validator = new DeveloperBlockerValidator();

	private static final String CAPABILITY_BLOCKER = """
			{"blockers": [{"code": "TOOL_CAPABILITY_MISSING"}]}""";

	@Test
	void passesWhenNoEvidenceExistsAtAllForThisExecution() {
		assertThat(validator.validate(CAPABILITY_BLOCKER, List.of()).valid()).isTrue();
	}

	@Test
	void passesWhenDeniedEvidenceSupportsTheCapabilityBlocker() {
		ToolExecution denied = toolExecution(ToolExecutionStatus.DENIED);

		assertThat(validator.validate(CAPABILITY_BLOCKER, List.of(denied)).valid()).isTrue();
	}

	@Test
	void passesWhenErrorEvidenceSupportsTheCapabilityBlocker() {
		ToolExecution errored = toolExecution(ToolExecutionStatus.ERROR);

		assertThat(validator.validate(CAPABILITY_BLOCKER, List.of(errored)).valid()).isTrue();
	}

	@Test
	void failsWhenEvidenceExistsButNoneOfItSupportsTheCapabilityBlocker() {
		ToolExecution succeeded = toolExecution(ToolExecutionStatus.SUCCEEDED);

		DeveloperResultValidationResult result = validator.validate(CAPABILITY_BLOCKER, List.of(succeeded));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.validator()).isEqualTo("blocker"));
	}

	@Test
	void neverCrossChecksACodeWithNoEvidenceSourceEvenWithEvidencePresent() {
		ToolExecution succeeded = toolExecution(ToolExecutionStatus.SUCCEEDED);
		String dependencyBlocker = """
				{"blockers": [{"code": "DEPENDENCY_POLICY_BLOCKED"}]}""";

		assertThat(validator.validate(dependencyBlocker, List.of(succeeded)).valid()).isTrue();
	}

	@Test
	void reportsMalformedJsonAsASingleIssue() {
		assertThat(validator.validate("not json", List.of()).valid()).isFalse();
	}

	private ToolExecution toolExecution(ToolExecutionStatus status) {
		Instant now = Instant.now();
		return new ToolExecution(UUID.randomUUID(), ToolCapability.PROJECT_EXECUTION, "install", 0, status, null, now, now);
	}
}
