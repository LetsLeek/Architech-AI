package ai.architech.backend.core.project;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void createsAWebsiteProjectAndRetrievesItById() throws Exception {
		String body = objectMapper.writeValueAsString(new CreateProjectRequest("website"));

		String response = mockMvc
				.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.projectType").value("website"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

		mockMvc.perform(get("/api/projects/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projectType").value("website"));
	}

	@Test
	void rejectsAnUnsupportedProjectType() throws Exception {
		String body = objectMapper.writeValueAsString(new CreateProjectRequest("mobile-app"));

		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsNotFoundForAnUnknownProjectId() throws Exception {
		mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}
}
