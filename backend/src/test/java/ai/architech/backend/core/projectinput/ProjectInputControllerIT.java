package ai.architech.backend.core.projectinput;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectInputControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProjectRepository projectRepository;

	@Test
	void submitsAndListsFreeTextInputsForAProject() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		String body = objectMapper.writeValueAsString(new SubmitProjectInputRequest("We are a bakery in Vienna."));

		mockMvc.perform(post("/api/projects/{id}/inputs", project.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("We are a bakery in Vienna."))
				.andExpect(jsonPath("$.projectId").value(project.getId().toString()));

		mockMvc.perform(get("/api/projects/{id}/inputs", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].content").value("We are a bakery in Vienna."));
	}

	@Test
	void rejectsSubmissionForAnUnknownProject() throws Exception {
		String body = objectMapper.writeValueAsString(new SubmitProjectInputRequest("irrelevant"));

		mockMvc.perform(post("/api/projects/{id}/inputs", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsListingForAnUnknownProject() throws Exception {
		mockMvc.perform(get("/api/projects/{id}/inputs", UUID.randomUUID())).andExpect(status().isNotFound());
	}
}
