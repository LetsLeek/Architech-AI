package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.evidence.EvidenceSnapshot;
import ai.architech.backend.core.evidence.EvidenceSnapshotFactory;
import ai.architech.backend.core.evidence.ReferencedSourceContext;
import ai.architech.backend.core.evidence.SourceContext;
import ai.architech.backend.core.evidence.SourceContextFactory;
import ai.architech.backend.core.evidence.SourceRefAssigner;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SourceRefValidatorIT {

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
	private SourceRefValidator sourceRefValidator;

	@Test
	void acceptsRefsThatBelongToTheActiveSnapshot() {
		ReferencedSourceContext referenced = referencedContextFor("We are a bakery in Vienna.");
		String realRef = referenced.items().get(0).sourceRef();

		SourceRefValidationResult result = sourceRefValidator.validate(referenced.evidenceSnapshotId(), Set.of(realRef));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsARefThatDoesNotExistAtAll() {
		ReferencedSourceContext referenced = referencedContextFor("We are a bakery in Vienna.");

		SourceRefValidationResult result =
				sourceRefValidator.validate(referenced.evidenceSnapshotId(), Set.of("SRC-999"));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).extracting(SourceRefValidationIssue::ref).containsExactly("SRC-999");
	}

	@Test
	void rejectsARefThatBelongsToADifferentSnapshot() {
		// Ref numbering restarts at SRC-1 per snapshot, so a single-item snapshot's only ref
		// would coincidentally string-match another single-item snapshot's ref. Give B two
		// items and use its second ref (SRC-2), which A - with only one item - never has.
		ReferencedSourceContext referencedA = referencedContextFor("Evidence for project A.");
		ReferencedSourceContext referencedB =
				referencedContextForTwoInputs("First evidence for project B.", "Second evidence for project B.");
		String refFromBOnly = referencedB.items().get(1).sourceRef();

		SourceRefValidationResult result =
				sourceRefValidator.validate(referencedA.evidenceSnapshotId(), Set.of(refFromBOnly));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).extracting(SourceRefValidationIssue::ref).containsExactly(refFromBOnly);
	}

	private ReferencedSourceContext referencedContextFor(String freeText) {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), freeText));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());
		SourceContext context = sourceContextFactory.build(snapshot.getId());
		return sourceRefAssigner.assignRefs(context);
	}

	private ReferencedSourceContext referencedContextForTwoInputs(String first, String second) {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), first));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), second));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());
		SourceContext context = sourceContextFactory.build(snapshot.getId());
		return sourceRefAssigner.assignRefs(context);
	}
}
