package ai.architech.backend.core.artifact;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
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
class ArtifactVersionControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private ArtifactVersionFactory artifactVersionFactory;

	@Test
	void rejectsAnUnknownProject() throws Exception {
		mockMvc.perform(get("/api/projects/{id}/artifacts/customer-profile", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsAProjectWithNoArtifactOfThatTypeYet() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(get("/api/projects/{id}/artifacts/customer-profile", project.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void returnsTheLatestCanonicalVersionAsParsedJson() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));

		artifactVersionFactory.createNextVersion(
				project.getId(), "customer-profile", execution.getId(), "{\"business\": {\"name\": \"v1\"}}");
		artifactVersionFactory.createNextVersion(
				project.getId(), "customer-profile", execution.getId(), "{\"business\": {\"name\": \"v2\"}}");

		mockMvc.perform(get("/api/projects/{id}/artifacts/customer-profile", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("customer-profile"))
				.andExpect(jsonPath("$.versionNumber").value(2))
				.andExpect(jsonPath("$.content.business.name").value("v2"));
	}
}
