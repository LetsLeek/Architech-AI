package ai.architech.backend.core.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.evidence.EvidenceSnapshot;
import ai.architech.backend.core.evidence.EvidenceSnapshotFactory;
import ai.architech.backend.core.evidence.EvidenceSnapshotNotFoundException;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AgentRunnerTests {

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
				.isInstanceOf(EvidenceSnapshotNotFoundException.class);
	}
}
