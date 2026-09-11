package ai.architech.backend.core.projectinput;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StructuredProjectInputRepositoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private StructuredProjectInputRepository structuredProjectInputRepository;

	@Test
	void savesAndReloadsStructuredFieldsExactlyAsSubmitted() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		Map<String, String> fields = Map.of("businessName", "Foo Bakery", "email", "foo@bakery.example");

		StructuredProjectInput saved =
				structuredProjectInputRepository.saveAndFlush(new StructuredProjectInput(project.getId(), fields));

		StructuredProjectInput reloaded =
				structuredProjectInputRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getFields()).isEqualTo(fields);
		assertThat(reloaded.getProjectId()).isEqualTo(project.getId());
	}
}
