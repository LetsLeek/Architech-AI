package ai.architech.backend.projecttype.website;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
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
class RequirementsAnalysisControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectInputRepository projectInputRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Test
	void rejectsStartingForAnUnknownProject() throws Exception {
		mockMvc.perform(post("/api/projects/{id}/requirements-analysis", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsStartingWithNoCustomerInputYet() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(post("/api/projects/{id}/requirements-analysis", project.getId()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void startsAnalysisAndReturnsATerminalStatus() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));

		// The only registered AI provider is the deterministic mock, which always returns
		// empty content - output-contract validation always fails, so this always ends up
		// FAILED. That's the point of this test: prove the endpoint really drives the full
		// pipeline end-to-end and reports a genuine terminal outcome, not that it succeeds.
		mockMvc.perform(post("/api/projects/{id}/requirements-analysis", project.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.executionId").exists())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.succeeded").value(false))
				.andExpect(jsonPath("$.validationIssues").isNotEmpty());
	}

	@Test
	void rejectsStartingWhenAnAnalysisIsAlreadyRunningForTheProject() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));
		AgentExecution running = new AgentExecution(project.getId(), "requirements-agent", 1);
		running.start();
		agentExecutionRepository.saveAndFlush(running);

		mockMvc.perform(post("/api/projects/{id}/requirements-analysis", project.getId()))
				.andExpect(status().isConflict());
	}
}
