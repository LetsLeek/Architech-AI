package ai.architech.backend.core.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EvidenceSnapshotRepositoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private EvidenceSnapshotRepository evidenceSnapshotRepository;

	@Test
	void savesAndReloadsAllThreeIdLists() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID textId = UUID.randomUUID();
		UUID structuredId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();

		EvidenceSnapshot saved = evidenceSnapshotRepository.saveAndFlush(
				new EvidenceSnapshot(project.getId(), List.of(textId), List.of(structuredId), List.of(fileId)));

		EvidenceSnapshot reloaded =
				evidenceSnapshotRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getProjectInputIds()).containsExactly(textId);
		assertThat(reloaded.getStructuredProjectInputIds()).containsExactly(structuredId);
		assertThat(reloaded.getFileProjectInputIds()).containsExactly(fileId);
	}
}
