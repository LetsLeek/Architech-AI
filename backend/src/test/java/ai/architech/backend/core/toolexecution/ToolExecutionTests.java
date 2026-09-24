package ai.architech.backend.core.toolexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolExecutionTests {

	@Test
	void recordsASuccessfulToolCall() {
		Instant startedAt = Instant.now();
		Instant finishedAt = startedAt.plusSeconds(1);

		ToolExecution execution = new ToolExecution(
				UUID.randomUUID(), ToolCapability.FILESYSTEM, "write", 0,
				ToolExecutionStatus.SUCCEEDED, "wrote 12 bytes", startedAt, finishedAt);

		assertThat(execution.getStatus()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		assertThat(execution.getDiagnosticSummary()).isEqualTo("wrote 12 bytes");
		assertThat(execution.getCorrectionCycle()).isZero();
	}

	@Test
	void recordsADeniedCapabilityCall() {
		Instant now = Instant.now();

		ToolExecution execution = new ToolExecution(
				UUID.randomUUID(), ToolCapability.GIT_INSPECTION, "checkout", 0,
				ToolExecutionStatus.DENIED, "checkout is not in the allowed git operation set", now, now);

		assertThat(execution.getStatus()).isEqualTo(ToolExecutionStatus.DENIED);
	}

	@Test
	void recordsAToolOrSourceFailure() {
		Instant startedAt = Instant.now();
		Instant finishedAt = startedAt.plusSeconds(3);

		ToolExecution execution = new ToolExecution(
				UUID.randomUUID(), ToolCapability.PROJECT_EXECUTION, "typecheck", 1,
				ToolExecutionStatus.FAILED, "error TS2322: Type 'string' is not assignable to type 'number'.",
				startedAt, finishedAt);

		assertThat(execution.getStatus()).isEqualTo(ToolExecutionStatus.FAILED);
		assertThat(execution.getCorrectionCycle()).isEqualTo(1);
	}

	@Test
	void recordsAnInfrastructureError() {
		Instant startedAt = Instant.now();
		Instant finishedAt = startedAt.plusSeconds(30);

		ToolExecution execution = new ToolExecution(
				UUID.randomUUID(), ToolCapability.PROJECT_EXECUTION, "install", 0,
				ToolExecutionStatus.ERROR, "process timed out after 30s", startedAt, finishedAt);

		assertThat(execution.getStatus()).isEqualTo(ToolExecutionStatus.ERROR);
	}

	@Test
	void rejectsANegativeCorrectionCycle() {
		Instant now = Instant.now();

		assertThatThrownBy(() -> new ToolExecution(
						UUID.randomUUID(), ToolCapability.FILESYSTEM, "read", -1,
						ToolExecutionStatus.SUCCEEDED, null, now, now))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAFinishTimeBeforeTheStartTime() {
		Instant startedAt = Instant.now();
		Instant finishedAt = startedAt.minusSeconds(1);

		assertThatThrownBy(() -> new ToolExecution(
						UUID.randomUUID(), ToolCapability.FILESYSTEM, "read", 0,
						ToolExecutionStatus.SUCCEEDED, null, startedAt, finishedAt))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void redactsSecretShapedContentOutOfTheDiagnosticSummaryBeforeStoringIt() {
		Instant now = Instant.now();

		ToolExecution execution = new ToolExecution(
				UUID.randomUUID(), ToolCapability.PROJECT_EXECUTION, "install", 0,
				ToolExecutionStatus.ERROR, "npm ERR! AKIAABCDEFGHIJKLMNOP leaked in output", now, now);

		assertThat(execution.getDiagnosticSummary())
				.contains("[REDACTED:AWS_ACCESS_KEY]")
				.doesNotContain("AKIAABCDEFGHIJKLMNOP");
	}

	@Test
	void boundsAnOverlongDiagnosticSummary() {
		Instant now = Instant.now();
		String hugeOutput = "x".repeat(10_000);

		ToolExecution execution = new ToolExecution(
				UUID.randomUUID(), ToolCapability.PROJECT_EXECUTION, "test", 0,
				ToolExecutionStatus.FAILED, hugeOutput, now, now);

		assertThat(execution.getDiagnosticSummary().length()).isLessThan(hugeOutput.length());
		assertThat(execution.getDiagnosticSummary()).endsWith("...[truncated]");
	}

	@Test
	void toleratesANullDiagnosticSummary() {
		Instant now = Instant.now();

		ToolExecution execution = new ToolExecution(
				UUID.randomUUID(), ToolCapability.FILESYSTEM, "read", 0,
				ToolExecutionStatus.SUCCEEDED, null, now, now);

		assertThat(execution.getDiagnosticSummary()).isNull();
	}
}
