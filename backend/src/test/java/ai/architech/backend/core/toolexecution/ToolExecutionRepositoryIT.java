package ai.architech.backend.core.toolexecution;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ToolExecutionRepositoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private ToolExecutionRepository toolExecutionRepository;

	@Test
	void savesAndReloadsAToolExecutionLinkedToItsAgentExecution() {
		AgentExecution execution = persistedExecution();
		Instant startedAt = Instant.now();
		Instant finishedAt = startedAt.plusSeconds(1);

		ToolExecution saved = toolExecutionRepository.saveAndFlush(new ToolExecution(
				execution.getId(), ToolCapability.FILESYSTEM, "write", 0,
				ToolExecutionStatus.SUCCEEDED, "wrote file", startedAt, finishedAt));

		ToolExecution reloaded = toolExecutionRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getAgentExecutionId()).isEqualTo(execution.getId());
		assertThat(reloaded.getCapability()).isEqualTo(ToolCapability.FILESYSTEM);
		assertThat(reloaded.getToolName()).isEqualTo("write");
	}

	@Test
	void keepsCorrelationCorrectAcrossBoundedCorrectionCyclesWithinOneExecution() {
		AgentExecution execution = persistedExecution();
		Instant t0 = Instant.now();

		toolExecutionRepository.saveAndFlush(new ToolExecution(
				execution.getId(), ToolCapability.PROJECT_EXECUTION, "typecheck", 0,
				ToolExecutionStatus.FAILED, "TS2322", t0, t0.plusSeconds(1)));
		toolExecutionRepository.saveAndFlush(new ToolExecution(
				execution.getId(), ToolCapability.FILESYSTEM, "patch", 1,
				ToolExecutionStatus.SUCCEEDED, "patched", t0.plusSeconds(2), t0.plusSeconds(3)));
		toolExecutionRepository.saveAndFlush(new ToolExecution(
				execution.getId(), ToolCapability.PROJECT_EXECUTION, "typecheck", 1,
				ToolExecutionStatus.SUCCEEDED, null, t0.plusSeconds(4), t0.plusSeconds(5)));

		var byOccurrence = toolExecutionRepository.findByAgentExecutionIdOrderByStartedAtAsc(execution.getId());

		assertThat(byOccurrence).extracting(ToolExecution::getCorrectionCycle).containsExactly(0, 1, 1);
		assertThat(byOccurrence).extracting(ToolExecution::getStatus)
				.containsExactly(ToolExecutionStatus.FAILED, ToolExecutionStatus.SUCCEEDED, ToolExecutionStatus.SUCCEEDED);
	}

	@Test
	void neverMixesToolExecutionsFromASeparateRetryAttempt() {
		AgentExecution firstAttempt = persistedExecution();
		AgentExecution retryAttempt = agentExecutionRepository.saveAndFlush(new AgentExecution(
				firstAttempt.getProjectId(), firstAttempt.getAgentId(), firstAttempt.getAgentVersion(),
				firstAttempt.getId(), "RUNNER_VERIFICATION_FAILURE"));
		Instant now = Instant.now();

		toolExecutionRepository.saveAndFlush(new ToolExecution(
				firstAttempt.getId(), ToolCapability.PROJECT_EXECUTION, "build", 0,
				ToolExecutionStatus.FAILED, "build failed", now, now.plusSeconds(1)));
		toolExecutionRepository.saveAndFlush(new ToolExecution(
				retryAttempt.getId(), ToolCapability.PROJECT_EXECUTION, "build", 0,
				ToolExecutionStatus.SUCCEEDED, "build ok", now.plusSeconds(2), now.plusSeconds(3)));

		var firstAttemptTools = toolExecutionRepository.findByAgentExecutionIdOrderByStartedAtAsc(firstAttempt.getId());
		var retryAttemptTools = toolExecutionRepository.findByAgentExecutionIdOrderByStartedAtAsc(retryAttempt.getId());

		assertThat(firstAttemptTools).singleElement().satisfies(t -> assertThat(t.getStatus()).isEqualTo(ToolExecutionStatus.FAILED));
		assertThat(retryAttemptTools).singleElement().satisfies(t -> assertThat(t.getStatus()).isEqualTo(ToolExecutionStatus.SUCCEEDED));
	}

	private AgentExecution persistedExecution() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		return agentExecutionRepository.saveAndFlush(new AgentExecution(project.getId(), "developer-agent", 1));
	}
}
