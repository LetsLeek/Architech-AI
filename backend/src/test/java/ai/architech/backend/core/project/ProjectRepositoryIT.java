package ai.architech.backend.core.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectRepositoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Test
	void savesAndReloadsAProjectWithGeneratedIdAndTimestamps() {
		Project project = new Project("website");

		Project saved = projectRepository.saveAndFlush(project);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();

		Project reloaded = projectRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getProjectType()).isEqualTo("website");
	}
}
