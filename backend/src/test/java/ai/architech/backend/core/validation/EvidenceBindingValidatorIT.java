package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.EvidenceRecord;
import ai.architech.backend.core.qa.EvidenceRecordRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-170's own three named scenarios: foreign Candidate Evidence, missing
 * context, and valid (explicit, compatible) reuse.
 */
@SpringBootTest
@Transactional
class EvidenceBindingValidatorIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private EvidenceRecordRepository evidenceRecordRepository;

	@Autowired
	private EvidenceBindingValidator validator;

	@Test
	void acceptsEvidenceBoundToTheCandidateUnderEvaluation() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		QaExecution execution = seedExecution(candidate);
		EvidenceRecord evidence = seedEvidence(execution, candidate, "/pricing", "viewport-desktop", null, null, null, null);

		EvidenceBindingResult result =
				validator.validate(candidate.getId(), RequiredEvidenceContext.none(), List.of(evidence.getId().toString()));

		assertThat(result.passed()).as(result.problems().toString()).isTrue();
	}

	@Test
	void rejectsAnEvidenceRefThatDoesNotResolveToAnyRow() {
		WebsiteImplementationCandidate candidate = seedCandidate();

		EvidenceBindingResult result =
				validator.validate(candidate.getId(), RequiredEvidenceContext.none(), List.of(UUID.randomUUID().toString()));

		assertThat(result.passed()).isFalse();
		assertThat(result.problems()).anySatisfy(problem -> assertThat(problem.problem()).contains("no Evidence exists"));
	}

	@Test
	void rejectsEvidenceCapturedAgainstAnotherCandidateWithNoReuseJustification() {
		WebsiteImplementationCandidate foreignCandidate = seedCandidate();
		QaExecution foreignExecution = seedExecution(foreignCandidate);
		EvidenceRecord foreignEvidence = seedEvidence(foreignExecution, foreignCandidate, null, null, null, null, null, null);

		WebsiteImplementationCandidate candidateUnderEvaluation = seedCandidate();

		EvidenceBindingResult result = validator.validate(
				candidateUnderEvaluation.getId(), RequiredEvidenceContext.none(), List.of(foreignEvidence.getId().toString()));

		assertThat(result.passed()).isFalse();
		assertThat(result.problems())
				.anySatisfy(problem -> assertThat(problem.problem())
						.contains("captured against Candidate " + foreignCandidate.getId())
						.contains("no compatible-reuse justification"));
	}

	@Test
	void acceptsEvidenceCapturedAgainstAnotherCandidateWhenExplicitCompatibleReuseIsRecorded() {
		WebsiteImplementationCandidate originalCandidate = seedCandidate();
		QaExecution originalExecution = seedExecution(originalCandidate);
		EvidenceRecord originalEvidence = seedEvidence(originalExecution, originalCandidate, null, null, null, null, null, null);

		WebsiteImplementationCandidate candidateUnderEvaluation = seedCandidate();
		QaExecution reuseExecution = seedExecution(candidateUnderEvaluation);
		EvidenceRecord reusedEvidence = seedEvidence(
				reuseExecution,
				originalCandidate,
				null,
				null,
				null,
				null,
				originalEvidence.getId(),
				"static asset manifest unchanged between these two Candidates' own builds");

		EvidenceBindingResult result = validator.validate(
				candidateUnderEvaluation.getId(), RequiredEvidenceContext.none(), List.of(reusedEvidence.getId().toString()));

		assertThat(result.passed()).as(result.problems().toString()).isTrue();
	}

	@Test
	void rejectsCitedEvidenceThatDoesNotCoverTheRequiredRouteContext() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		QaExecution execution = seedExecution(candidate);
		EvidenceRecord evidence = seedEvidence(execution, candidate, "/about", "viewport-desktop", null, null, null, null);

		EvidenceBindingResult result = validator.validate(
				candidate.getId(), new RequiredEvidenceContext("/pricing", null, null, null), List.of(evidence.getId().toString()));

		assertThat(result.passed()).isFalse();
		assertThat(result.problems())
				.anySatisfy(problem -> assertThat(problem.problem()).contains("route '/pricing'"));
	}

	@Test
	void acceptsWhenAtLeastOneCitedEvidenceItemCoversEachRequiredContextField() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		QaExecution execution = seedExecution(candidate);
		EvidenceRecord routeEvidence = seedEvidence(execution, candidate, "/pricing", null, null, null, null, null);
		EvidenceRecord viewportEvidence = seedEvidence(execution, candidate, null, "viewport-mobile", null, null, null, null);

		EvidenceBindingResult result = validator.validate(
				candidate.getId(),
				new RequiredEvidenceContext("/pricing", "viewport-mobile", null, null),
				List.of(routeEvidence.getId().toString(), viewportEvidence.getId().toString()));

		assertThat(result.passed()).as(result.problems().toString()).isTrue();
	}

	private WebsiteImplementationCandidate seedCandidate() {
		UUID projectId = projectRepository.saveAndFlush(new Project("website")).getId();
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				"design-v1",
				"prop-a",
				"runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				"[]",
				"[]"));
	}

	private QaExecution seedExecution(WebsiteImplementationCandidate candidate) {
		AgentExecution qaAgentExecution = new AgentExecution(candidate.getProjectId(), "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		return qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", null, "website-qa-tools@1.0.0"));
	}

	private EvidenceRecord seedEvidence(
			QaExecution execution,
			WebsiteImplementationCandidate testedCandidate,
			String route,
			String viewportRef,
			String locale,
			String interactionState,
			UUID reusedFromEvidenceId,
			String reuseJustification) {
		EvidenceManifest manifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(execution.getId()));
		return evidenceRecordRepository.saveAndFlush(new EvidenceRecord(
				manifest.getId(),
				execution.getId(),
				testedCandidate.getId(),
				EvidenceRecord.Kind.SCREENSHOT,
				"check:CRITICAL_ELEMENT_VIEWPORT_VISIBILITY",
				route,
				viewportRef,
				locale,
				interactionState,
				"observed content",
				reusedFromEvidenceId,
				reuseJustification));
	}
}
