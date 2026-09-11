package ai.architech.backend.core.projectinput;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FileProjectInputRepositoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private FileProjectInputRepository fileProjectInputRepository;

	@Test
	void savesAndReloadsFileMetadataAndContent() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		byte[] content = "hello evidence".getBytes(StandardCharsets.UTF_8);

		FileProjectInput saved = fileProjectInputRepository.saveAndFlush(
				new FileProjectInput(project.getId(), "notes.txt", "text/plain", content));

		FileProjectInput reloaded =
				fileProjectInputRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getFilename()).isEqualTo("notes.txt");
		assertThat(reloaded.getContentType()).isEqualTo("text/plain");
		assertThat(reloaded.getSizeBytes()).isEqualTo(content.length);
		assertThat(reloaded.getProjectId()).isEqualTo(project.getId());
	}
}
