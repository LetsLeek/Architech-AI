package ai.architech.backend.core.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.FileProjectInput;
import ai.architech.backend.core.projectinput.FileProjectInputRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import ai.architech.backend.core.projectinput.StructuredProjectInput;
import ai.architech.backend.core.projectinput.StructuredProjectInputRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EvidenceSnapshotFactoryTests {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectInputRepository projectInputRepository;

	@Autowired
	private StructuredProjectInputRepository structuredProjectInputRepository;

	@Autowired
	private FileProjectInputRepository fileProjectInputRepository;

	@Autowired
	private EvidenceSnapshotFactory evidenceSnapshotFactory;

	@Test
	void capturesExactlyTheEvidenceThatExistsAtSnapshotTime() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		ProjectInput freeText = projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "we are a bakery"));
		StructuredProjectInput structured = structuredProjectInputRepository.saveAndFlush(
				new StructuredProjectInput(project.getId(), Map.of("businessName", "Foo Bakery")));
		FileProjectInput file = fileProjectInputRepository.saveAndFlush(new FileProjectInput(
				project.getId(), "notes.txt", "text/plain", "content".getBytes(StandardCharsets.UTF_8)));

		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());

		assertThat(snapshot.getProjectId()).isEqualTo(project.getId());
		assertThat(snapshot.getProjectInputIds()).containsExactly(freeText.getId());
		assertThat(snapshot.getStructuredProjectInputIds()).containsExactly(structured.getId());
		assertThat(snapshot.getFileProjectInputIds()).containsExactly(file.getId());
		assertThat(snapshot.getCreatedAt()).isNotNull();
	}

	@Test
	void evidenceAddedAfterTheSnapshotDoesNotRetroactivelyAppearInIt() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "first note"));

		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());
		assertThat(snapshot.getProjectInputIds()).hasSize(1);

		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "second note, added after the snapshot"));

		assertThat(snapshot.getProjectInputIds()).hasSize(1);
	}
}
