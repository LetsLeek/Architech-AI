package ai.architech.backend.core.projectinput;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.Map;
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
class StructuredProjectInputControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProjectRepository projectRepository;

	@Test
	void submitsAndListsStructuredInputsForAProject() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		String body = objectMapper.writeValueAsString(
				new SubmitStructuredProjectInputRequest(Map.of("businessName", "Foo Bakery")));

		mockMvc.perform(post("/api/projects/{id}/structured-inputs", project.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fields.businessName").value("Foo Bakery"))
				.andExpect(jsonPath("$.projectId").value(project.getId().toString()));

		mockMvc.perform(get("/api/projects/{id}/structured-inputs", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].fields.businessName").value("Foo Bakery"));
	}

	@Test
	void rejectsSubmissionForAnUnknownProject() throws Exception {
		String body =
				objectMapper.writeValueAsString(new SubmitStructuredProjectInputRequest(Map.of("k", "v")));

		mockMvc.perform(post("/api/projects/{id}/structured-inputs", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isNotFound());
	}
}
