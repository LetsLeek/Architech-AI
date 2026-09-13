package ai.architech.backend.core.agentexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.ai.AiResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentExecutionTests {

	@Test
	void startsAsPendingAndMovesToSucceededViaRunning() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);
		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.PENDING);

		execution.start();
		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);
		assertThat(execution.getStartedAt()).isNotNull();

		execution.succeed();
		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(execution.getFinishedAt()).isNotNull();
	}

	@Test
	void canFailFromRunning() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);
		execution.start();

		execution.fail("model timed out");

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(execution.getFailureReason()).isEqualTo("model timed out");
		assertThat(execution.getFinishedAt()).isNotNull();
	}

	@Test
	void canFailBeforeEverStarting() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);

		execution.fail("agent definition not found");

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
	}

	@Test
	void cannotSucceedWithoutHavingStarted() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);

		assertThatThrownBy(execution::succeed).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cannotStartTwice() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);
		execution.start();

		assertThatThrownBy(execution::start).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cannotFailAnExecutionThatAlreadyFinished() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);
		execution.start();
		execution.succeed();

		assertThatThrownBy(() -> execution.fail("too late")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void canBlockFromRunning() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);
		execution.start();

		execution.block("missing integration contract for the required booking function");

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.BLOCKED);
		assertThat(execution.getFailureReason()).isEqualTo("missing integration contract for the required booking function");
		assertThat(execution.getFinishedAt()).isNotNull();
	}

	@Test
	void cannotBlockWithoutHavingStarted() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);

		assertThatThrownBy(() -> execution.block("reason")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void canErrorFromRunningOrBeforeStarting() {
		AgentExecution runningExecution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);
		runningExecution.start();
		runningExecution.error("sandbox provisioning failed");
		assertThat(runningExecution.getStatus()).isEqualTo(AgentExecutionStatus.ERROR);
		assertThat(runningExecution.getFinishedAt()).isNotNull();

		AgentExecution pendingExecution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);
		pendingExecution.error("agent definition not found");
		assertThat(pendingExecution.getStatus()).isEqualTo(AgentExecutionStatus.ERROR);
	}

	@Test
	void aBlockedExecutionCannotLaterBeFailedOrErroredOrSucceeded() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);
		execution.start();
		execution.block("nonlocal blocker");

		assertThatThrownBy(() -> execution.fail("too late")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> execution.error("too late")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(execution::succeed).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anErroredExecutionCannotLaterTransitionAgain() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);
		execution.error("infrastructure failure");

		assertThatThrownBy(() -> execution.fail("too late")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> execution.block("too late")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void recordsRetryLineageAgainstThePriorExecution() {
		UUID priorExecutionId = UUID.randomUUID();

		AgentExecution retry = new AgentExecution(
				UUID.randomUUID(), "developer-agent", 1, priorExecutionId, "RUNNER_VERIFICATION_FAILURE");

		assertThat(retry.getRetryOfExecutionId()).isEqualTo(priorExecutionId);
		assertThat(retry.getRetryReasonCode()).isEqualTo("RUNNER_VERIFICATION_FAILURE");
		assertThat(retry.getStatus()).isEqualTo(AgentExecutionStatus.PENDING);
		// A retry is always a brand new row - never linked back by mutating the prior execution.
		assertThat(retry.getId()).isNotEqualTo(priorExecutionId);
	}

	@Test
	void anOrdinaryExecutionHasNoRetryLineage() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);

		assertThat(execution.getRetryOfExecutionId()).isNull();
		assertThat(execution.getRetryReasonCode()).isNull();
	}

	@Test
	void recordsModelUsageIndependentlyOfStatus() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);

		execution.recordModelUsage("mock", "mock-model", 120, 340, new BigDecimal("0.0042"));

		assertThat(execution.getProvider()).isEqualTo("mock");
		assertThat(execution.getModel()).isEqualTo("mock-model");
		assertThat(execution.getPromptTokens()).isEqualTo(120);
		assertThat(execution.getCompletionTokens()).isEqualTo(340);
		assertThat(execution.getCostUsd()).isEqualByComparingTo("0.0042");
	}

	@Test
	void recordsModelUsageDirectlyFromAnAiResponse() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);
		AiResponse response = new AiResponse("mock", "mock-model", "content", "corr-1", 50, 75, null, null);

		execution.recordModelUsage(response, new BigDecimal("0.01"));

		assertThat(execution.getProvider()).isEqualTo("mock");
		assertThat(execution.getModel()).isEqualTo("mock-model");
		assertThat(execution.getPromptTokens()).isEqualTo(50);
		assertThat(execution.getCompletionTokens()).isEqualTo(75);
		assertThat(execution.getCostUsd()).isEqualByComparingTo("0.01");
	}
}
