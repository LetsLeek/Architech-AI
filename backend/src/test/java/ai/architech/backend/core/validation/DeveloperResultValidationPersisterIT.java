package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DeveloperResultValidationPersisterIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private DeveloperResultValidationPersister persister;

	@Autowired
	private DeveloperResultValidationRecordRepository repository;

	@Test
	void persistsAPassingResultWithNoIssues() {
		AgentExecution execution = seedExecution();

		DeveloperResultValidationRecord record = persister.persist(execution.getId(), DeveloperResultValidationResult.passed());

		assertThat(record.isValid()).isTrue();
		assertThat(record.getIssuesJson()).isEqualTo("[]");
		assertThat(repository.findByAgentExecutionIdOrderByCreatedAtAsc(execution.getId())).containsExactly(record);
	}

	@Test
	void persistsAFailingResultWithItsIssuesAsAJsonArray() {
		AgentExecution execution = seedExecution();
		DeveloperResultValidationResult result = new DeveloperResultValidationResult(
				false,
				List.of(
						new DeveloperResultValidationIssue("schema", "$.resultType", "must be one of the declared enum values"),
						new DeveloperResultValidationIssue("reference", "req-func-1", "requirementRef does not exist")));

		DeveloperResultValidationRecord record = persister.persist(execution.getId(), result);

		assertThat(record.isValid()).isFalse();
		assertThat(record.getIssuesJson())
				.contains("\"validator\":\"schema\"")
				.contains("\"reason\":\"must be one of the declared enum values\"")
				.contains("\"validator\":\"reference\"")
				.contains("\"ref\":\"req-func-1\"");
	}

	@Test
	void accumulatesMultipleRecordsAcrossCorrectionCyclesWithoutOverwriting() {
		AgentExecution execution = seedExecution();

		DeveloperResultValidationRecord first = persister.persist(
				execution.getId(),
				new DeveloperResultValidationResult(
						false, List.of(new DeveloperResultValidationIssue("schema", "$.foo", "missing"))));
		DeveloperResultValidationRecord second = persister.persist(execution.getId(), DeveloperResultValidationResult.passed());

		assertThat(repository.findByAgentExecutionIdOrderByCreatedAtAsc(execution.getId()))
				.containsExactly(first, second);
	}

	private AgentExecution seedExecution() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = new AgentExecution(project.getId(), "developer-agent", 1);
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}
}
