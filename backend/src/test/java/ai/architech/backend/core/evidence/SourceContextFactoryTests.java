package ai.architech.backend.core.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.error.ApplicationException;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SourceContextFactoryTests {

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

	@Autowired
	private SourceContextFactory sourceContextFactory;

	@Test
	void buildsReadableSourceItemsForEveryEvidenceTypeInTheSnapshot() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ProjectInput freeText =
				projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));
		StructuredProjectInput structured = structuredProjectInputRepository.saveAndFlush(
				new StructuredProjectInput(project.getId(), Map.of("businessName", "Foo Bakery")));
		FileProjectInput file = fileProjectInputRepository.saveAndFlush(new FileProjectInput(
				project.getId(), "notes.txt", "text/plain", "content".getBytes(StandardCharsets.UTF_8)));

		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());
		SourceContext context = sourceContextFactory.build(snapshot.getId());

		assertThat(context.evidenceSnapshotId()).isEqualTo(snapshot.getId());
		assertThat(context.items()).hasSize(3);

		SourceItem freeTextItem = itemFor(context, freeText.getId());
		assertThat(freeTextItem.origin()).isEqualTo(SourceOrigin.FREE_TEXT);
		assertThat(freeTextItem.content()).isEqualTo("We are a bakery in Vienna.");

		SourceItem structuredItem = itemFor(context, structured.getId());
		assertThat(structuredItem.origin()).isEqualTo(SourceOrigin.STRUCTURED);
		assertThat(structuredItem.content()).contains("businessName: Foo Bakery");

		SourceItem fileItem = itemFor(context, file.getId());
		assertThat(fileItem.origin()).isEqualTo(SourceOrigin.FILE);
		assertThat(fileItem.content()).contains("notes.txt").contains("text/plain");
	}

	@Test
	void throwsForAnUnknownEvidenceSnapshot() {
		assertThatThrownBy(() -> sourceContextFactory.build(UUID.randomUUID())).isInstanceOf(ApplicationException.class);
	}

	private static SourceItem itemFor(SourceContext context, UUID id) {
		return context.items().stream()
				.filter(item -> item.id().equals(id))
				.findFirst()
				.orElseThrow();
	}
}
