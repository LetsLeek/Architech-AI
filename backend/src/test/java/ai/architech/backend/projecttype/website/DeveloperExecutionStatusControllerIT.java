package ai.architech.backend.projecttype.website;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeveloperExecutionStatusControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Test
	void rejectsAnUnknownProject() throws Exception {
		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", UUID.randomUUID(), UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsAnUnknownExecution() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsAnExecutionBelongingToADifferentProject() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		Project otherProject = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution =
				agentExecutionRepository.saveAndFlush(new AgentExecution(otherProject.getId(), "developer-agent", 1));

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), execution.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsAnExecutionBelongingToADifferentAgent() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(project.getId(), "designer-agent", 1));

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), execution.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void reportsARunningExecutionWithNoCandidateAndNoFailureReason() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = new AgentExecution(project.getId(), "developer-agent", 1);
		execution.start();
		agentExecutionRepository.saveAndFlush(execution);

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), execution.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.executionId").value(execution.getId().toString()))
				.andExpect(jsonPath("$.status").value("RUNNING"))
				.andExpect(jsonPath("$.targetDesignArtifactVersionRef").value(nullValue()))
				.andExpect(jsonPath("$.targetDesignProposalLocalRef").value(nullValue()))
				.andExpect(jsonPath("$.candidateId").value(nullValue()))
				.andExpect(jsonPath("$.repositoryStateRef").value(nullValue()))
				.andExpect(jsonPath("$.failureReason").value(nullValue()));
	}

	@Test
	void reportsABlockedExecutionsFailureReasonWithoutACandidate() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = new AgentExecution(project.getId(), "developer-agent", 1);
		execution.start();
		execution.block("integration contract required for payment provider is missing");
		agentExecutionRepository.saveAndFlush(execution);

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), execution.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BLOCKED"))
				.andExpect(jsonPath("$.failureReason").value("integration contract required for payment provider is missing"))
				.andExpect(jsonPath("$.candidateId").value(nullValue()));
	}

	@Test
	void reportsAFailedExecutionsFailureReasonWithoutACandidate() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = new AgentExecution(project.getId(), "developer-agent", 1);
		execution.start();
		execution.fail("output-contract validation failed");
		agentExecutionRepository.saveAndFlush(execution);

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), execution.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureReason").value("output-contract validation failed"))
				.andExpect(jsonPath("$.candidateId").value(nullValue()));
	}

	@Test
	void reportsAnErroredExecutionsFailureReasonWithoutACandidate() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = new AgentExecution(project.getId(), "developer-agent", 1);
		execution.error("sandbox failed to start");
		agentExecutionRepository.saveAndFlush(execution);

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), execution.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ERROR"))
				.andExpect(jsonPath("$.failureReason").value("sandbox failed to start"))
				.andExpect(jsonPath("$.candidateId").value(nullValue()));
	}

	@Test
	void reportsASucceededExecutionWithItsOneAcceptedCandidate() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = new AgentExecution(project.getId(), "developer-agent", 1);
		execution.start();
		execution.succeed();
		agentExecutionRepository.saveAndFlush(execution);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				project.getId(),
				execution.getId(),
				"design-v1",
				"prop-a",
				"runtime-v1",
				"snapshot-hash-1",
				"summary",
				"[]",
				"[]",
				"[]"));

		mockMvc.perform(get("/api/projects/{projectId}/developer-executions/{executionId}", project.getId(), execution.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.targetDesignArtifactVersionRef").value("design-v1"))
				.andExpect(jsonPath("$.targetDesignProposalLocalRef").value("prop-a"))
				.andExpect(jsonPath("$.candidateId").value(candidate.getId().toString()))
				.andExpect(jsonPath("$.repositoryStateRef").value("snapshot-hash-1"))
				.andExpect(jsonPath("$.failureReason").value(nullValue()));
	}
}
