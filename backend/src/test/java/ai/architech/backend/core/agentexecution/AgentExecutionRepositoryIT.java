package ai.architech.backend.core.agentexecution;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AgentExecutionRepositoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Test
	void persistsAFullLifecycleWithModelUsage() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		AgentExecution execution = new AgentExecution(project.getId(), "requirements-agent", 1);
		execution.start();
		execution.recordModelUsage("mock", "mock-model", 100, 200, new BigDecimal("0.0015"));
		execution.succeed();

		AgentExecution saved = agentExecutionRepository.saveAndFlush(execution);
		AgentExecution reloaded =
				agentExecutionRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getProjectId()).isEqualTo(project.getId());
		assertThat(reloaded.getAgentId()).isEqualTo("requirements-agent");
		assertThat(reloaded.getAgentVersion()).isEqualTo(1);
		assertThat(reloaded.getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(reloaded.getProvider()).isEqualTo("mock");
		assertThat(reloaded.getCostUsd()).isEqualByComparingTo("0.0015");
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getStartedAt()).isNotNull();
		assertThat(reloaded.getFinishedAt()).isNotNull();
	}
}
