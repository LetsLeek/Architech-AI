package ai.architech.backend.core.projectinput;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectInputRepositoryTests {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectInputRepository projectInputRepository;

	@Test
	void savesInputsForAProjectInSubmissionOrder() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		ProjectInput first = projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "first note"));
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		ProjectInput second = projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "second note"));

		var inputs = projectInputRepository.findByProjectIdOrderByCreatedAtAsc(project.getId());

		assertThat(inputs).extracting(ProjectInput::getId).containsExactly(first.getId(), second.getId());
		assertThat(inputs).extracting(ProjectInput::getContent).containsExactly("first note", "second note");
	}
}
