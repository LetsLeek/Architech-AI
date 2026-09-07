package ai.architech.backend.core.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.evidence.EvidenceSnapshot;
import ai.architech.backend.core.evidence.EvidenceSnapshotFactory;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BoundedRetryAgentRunnerTests {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectInputRepository projectInputRepository;

	@Autowired
	private EvidenceSnapshotFactory evidenceSnapshotFactory;

	@Autowired
	private BoundedRetryAgentRunner boundedRetryAgentRunner;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private RunnerProperties runnerProperties;

	@Test
	void returnsTheFirstSuccessfulAttemptWithoutRetrying() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());

		RunnerResult result = boundedRetryAgentRunner.runWithRetries(snapshot.getId(), "requirements-agent", 1);

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);

		long executionsForThisAttempt = agentExecutionRepository.findAll().stream()
				.filter(e -> e.getAgentId().equals("requirements-agent"))
				.count();
		assertThat(executionsForThisAttempt).isEqualTo(1);
	}

	@Test
	void createsOneNewAgentExecutionPerAttemptAndThrowsAfterExhaustingTheBudget() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());

		assertThatThrownBy(() -> boundedRetryAgentRunner.runWithRetries(snapshot.getId(), "does-not-exist", 1))
				.isInstanceOf(RetryBudgetExhaustedException.class)
				.hasCauseInstanceOf(AgentRunnerException.class);

		var executions = agentExecutionRepository.findAll().stream()
				.filter(e -> e.getAgentId().equals("does-not-exist"))
				.toList();
		assertThat(executions).hasSize(runnerProperties.maxAttempts());
		assertThat(executions).allMatch(e -> e.getStatus() == AgentExecutionStatus.FAILED);
		// each attempt is its own execution, not a shared/reused row
		assertThat(executions.stream().map(e -> e.getId()).distinct().count()).isEqualTo(runnerProperties.maxAttempts());
	}
}
