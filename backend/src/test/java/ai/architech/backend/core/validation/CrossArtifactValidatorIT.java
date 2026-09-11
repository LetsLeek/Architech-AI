package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CrossArtifactValidatorIT {

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
	private CrossArtifactValidator crossArtifactValidator;

	@Test
	void acceptsWhenBothArtifactsOnlyCiteRefsFromTheActiveSnapshot() {
		ReferencedSourceContext referenced = referencedContextFor("We are a bakery in Vienna.");
		String realRef = referenced.items().get(0).sourceRef();

		CrossArtifactValidationResult result = crossArtifactValidator.validate(
				referenced.evidenceSnapshotId(), customerProfileCiting(realRef), websiteRequirementsCiting(realRef));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsWhenTheCustomerProfileCitesARefOutsideTheActiveSnapshot() {
		ReferencedSourceContext referenced = referencedContextFor("We are a bakery in Vienna.");
		String realRef = referenced.items().get(0).sourceRef();

		CrossArtifactValidationResult result = crossArtifactValidator.validate(
				referenced.evidenceSnapshotId(), customerProfileCiting("SRC-999"), websiteRequirementsCiting(realRef));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues())
				.extracting(CrossArtifactValidationIssue::artifactType, CrossArtifactValidationIssue::ref)
				.containsExactly(tuple("customer-profile", "SRC-999"));
	}

	@Test
	void rejectsWhenTheWebsiteRequirementsCiteARefOutsideTheActiveSnapshot() {
		ReferencedSourceContext referenced = referencedContextFor("We are a bakery in Vienna.");
		String realRef = referenced.items().get(0).sourceRef();

		CrossArtifactValidationResult result = crossArtifactValidator.validate(
				referenced.evidenceSnapshotId(), customerProfileCiting(realRef), websiteRequirementsCiting("SRC-999"));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues())
				.extracting(CrossArtifactValidationIssue::artifactType, CrossArtifactValidationIssue::ref)
				.containsExactly(tuple("website-requirements", "SRC-999"));
	}

	@Test
	void rejectsWhenARefBelongsToADifferentSnapshotEntirely() {
		// Ref numbering restarts at SRC-1 per snapshot, so a single-item snapshot's only ref
		// would coincidentally string-match another single-item snapshot's ref. Give B two
		// items and use its second ref (SRC-2), which A - with only one item - never has.
		ReferencedSourceContext referencedA = referencedContextFor("Evidence for project A.");
		ReferencedSourceContext referencedB =
				referencedContextForTwoInputs("First evidence for project B.", "Second evidence for project B.");
		String refFromBOnly = referencedB.items().get(1).sourceRef();

		CrossArtifactValidationResult result = crossArtifactValidator.validate(
				referencedA.evidenceSnapshotId(), customerProfileCiting(refFromBOnly), websiteRequirementsCiting(refFromBOnly));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).hasSize(2);
	}

	@Test
	void reportsUnparseableCandidateJsonAsAFailureRatherThanThrowing() {
		CrossArtifactValidationResult result =
				crossArtifactValidator.validate(UUID.randomUUID(), "not json {{{", websiteRequirementsCiting("SRC-1"));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.artifactType().equals("customer-profile"));
	}

	private String customerProfileCiting(String ref) {
		return """
				{"providedClaims": [{"claim": "Family owned since 1990", "sourceRefs": ["%s"]}]}
				"""
				.formatted(ref);
	}

	private String websiteRequirementsCiting(String ref) {
		return """
				{"goals": [{"localRef": "goal-1", "description": "Grow leads", "strength": "must", "sourceRefs": ["%s"]}]}
				"""
				.formatted(ref);
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
