package ai.architech.backend.core.projectinput;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FileProjectInputControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Test
	void uploadsAndListsAFileForAProject() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		MockMultipartFile file = new MockMultipartFile(
				"file", "notes.txt", "text/plain", "hello evidence".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/projects/{id}/files", project.getId()).file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.filename").value("notes.txt"))
				.andExpect(jsonPath("$.contentType").value("text/plain"))
				.andExpect(jsonPath("$.sizeBytes").value(14));

		mockMvc.perform(get("/api/projects/{id}/files", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].filename").value("notes.txt"));
	}

	@Test
	void rejectsAnEmptyFile() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

		mockMvc.perform(multipart("/api/projects/{id}/files", project.getId()).file(emptyFile))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsUploadForAnUnknownProject() throws Exception {
		MockMultipartFile file =
				new MockMultipartFile("file", "notes.txt", "text/plain", "content".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/projects/{id}/files", UUID.randomUUID()).file(file))
				.andExpect(status().isNotFound());
	}
}
