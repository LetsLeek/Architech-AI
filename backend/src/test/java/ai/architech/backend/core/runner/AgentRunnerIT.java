package ai.architech.backend.core.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.evidence.EvidenceSnapshot;
import ai.architech.backend.core.evidence.EvidenceSnapshotFactory;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AgentRunnerIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectInputRepository projectInputRepository;

	@Autowired
	private EvidenceSnapshotFactory evidenceSnapshotFactory;

	@Autowired
	private AgentRunner agentRunner;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Test
	void runsTheRealFrozenRequirementsAgentAgainstTheMockProviderAndStopsBeforeSuccess() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());

		RunnerResult result = agentRunner.run(snapshot.getId(), "requirements-agent", 1);

		// Candidate was received, but success requires validation+persistence (not built yet) -
		// so the execution deliberately stays RUNNING, never SUCCEEDED.
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);
		assertThat(result.execution().getProvider()).isEqualTo("mock");
		assertThat(result.execution().getModel()).isEqualTo("mock-model");
		assertThat(result.candidateOutput()).isEqualTo("");

		var persisted = agentExecutionRepository.findById(result.execution().getId()).orElseThrow();
		assertThat(persisted.getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);
		assertThat(persisted.getAgentId()).isEqualTo("requirements-agent");
	}

	@Test
	void marksTheExecutionFailedAndRethrowsWhenTheAgentDoesNotExist() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());

		assertThatThrownBy(() -> agentRunner.run(snapshot.getId(), "does-not-exist", 1))
				.isInstanceOf(AgentRunnerException.class);

		var executions = agentExecutionRepository.findAll().stream()
				.filter(e -> e.getAgentId().equals("does-not-exist"))
				.toList();
		assertThat(executions).hasSize(1);
		assertThat(executions.get(0).getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
	}

	@Test
	void throwsForAnUnknownEvidenceSnapshot() {
		assertThatThrownBy(() -> agentRunner.run(UUID.randomUUID(), "requirements-agent", 1))
				.isInstanceOf(ApplicationException.class);
	}

	@Test
	void blocksInvocationAndFailsTheExecutionWhenTheAgentsHardBudgetLimitIsAlreadyReached() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());

		// Seeds prior spend for this agent already at application.yml's configured default
		// hard limit ($5) - proves AiUsageBudgetGuard is wired end-to-end into AgentRunner
		// without spending any real money or needing a real AI provider.
		AgentExecution priorExecution = new AgentExecution(project.getId(), "requirements-agent", 1);
		priorExecution.recordModelUsage("anthropic", "claude-sonnet-5", 1000, 100, new BigDecimal("5.00"));
		agentExecutionRepository.saveAndFlush(priorExecution);

		assertThatThrownBy(() -> agentRunner.run(snapshot.getId(), "requirements-agent", 1))
				.isInstanceOf(ApplicationException.class)
				.satisfies(thrown -> assertThat(((ApplicationException) thrown).errorCode())
						.isEqualTo(ErrorCode.AI_BUDGET_HARD_LIMIT_EXCEEDED));

		var newExecutionsForThisProject = agentExecutionRepository.findAll().stream()
				.filter(e -> e.getProjectId().equals(project.getId()) && !e.getId().equals(priorExecution.getId()))
				.toList();
		assertThat(newExecutionsForThisProject).hasSize(1);
		assertThat(newExecutionsForThisProject.get(0).getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(newExecutionsForThisProject.get(0).getFailureReason()).contains("hard limit exceeded");
	}
}
