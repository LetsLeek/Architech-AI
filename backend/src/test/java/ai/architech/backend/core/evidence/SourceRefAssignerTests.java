package ai.architech.backend.core.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SourceRefAssignerTests {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectInputRepository projectInputRepository;

	@Autowired
	private EvidenceSnapshotFactory evidenceSnapshotFactory;

	@Autowired
	private SourceContextFactory sourceContextFactory;

	@Autowired
	private SourceRefAssigner sourceRefAssigner;

	@Autowired
	private SourceRefRepository sourceRefRepository;

	@Test
	void assignsDistinctOpaqueRefsToEveryItemAndPersistsTheMapping() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "first note"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "second note"));

		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());
		SourceContext context = sourceContextFactory.build(snapshot.getId());

		ReferencedSourceContext referenced = sourceRefAssigner.assignRefs(context);

		assertThat(referenced.evidenceSnapshotId()).isEqualTo(snapshot.getId());
		List<String> refs = referenced.items().stream().map(ReferencedSourceItem::sourceRef).toList();
		assertThat(refs).doesNotHaveDuplicates();
		assertThat(refs).containsExactlyInAnyOrder("SRC-1", "SRC-2");

		// persisted, and maps back to the right origin
		for (ReferencedSourceItem item : referenced.items()) {
			SourceRef persisted =
					sourceRefRepository.findByEvidenceSnapshotIdAndRef(snapshot.getId(), item.sourceRef()).orElseThrow();
			assertThat(persisted.getOrigin()).isEqualTo(SourceOrigin.FREE_TEXT);
		}
	}

	@Test
	void reusesTheSameRefsWhenCalledAgainForTheSameSnapshot() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "only note"));

		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());
		SourceContext context = sourceContextFactory.build(snapshot.getId());

		ReferencedSourceContext first = sourceRefAssigner.assignRefs(context);
		ReferencedSourceContext second = sourceRefAssigner.assignRefs(context);

		assertThat(second.items().get(0).sourceRef()).isEqualTo(first.items().get(0).sourceRef());
		assertThat(sourceRefRepository.findByEvidenceSnapshotId(snapshot.getId())).hasSize(1);
	}
}
