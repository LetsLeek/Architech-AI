package ai.architech.backend.core.agentexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void recordsModelUsageIndependentlyOfStatus() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "requirements-agent", 1);

		execution.recordModelUsage("mock", "mock-model", 120, 340, new BigDecimal("0.0042"));

		assertThat(execution.getProvider()).isEqualTo("mock");
		assertThat(execution.getModel()).isEqualTo("mock-model");
		assertThat(execution.getPromptTokens()).isEqualTo(120);
		assertThat(execution.getCompletionTokens()).isEqualTo(340);
		assertThat(execution.getCostUsd()).isEqualByComparingTo("0.0042");
	}
}
