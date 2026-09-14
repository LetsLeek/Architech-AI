package ai.architech.backend.core.qa;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-168's own acceptance criteria against a realistic multi-row QA
 * graph: stable ids and exact Candidate/Execution/Profile bindings, {@code QaResult} referencing
 * rather than duplicating its Finding/Issue/PolicyEvaluation content, and a full Re-QA
 * (Remediation) cycle that relates an old Finding to a new Candidate without mutating the old
 * Finding at all - exactly {@code rules/lineage.md}'s own invariant.
 */
@SpringBootTest
@Transactional
class QaPersistenceModelIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private AuthorityIssueRepository authorityIssueRepository;

	@Autowired
	private EvaluationIssueRepository evaluationIssueRepository;

	@Autowired
	private PolicyEvaluationRepository policyEvaluationRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private RemediationAssessmentRepository remediationAssessmentRepository;

	@Test
	void persistsAFullQaResultGraphWithExactBindingsAndReferencesRatherThanDuplicatedContent() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, "prop-a");

		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				seedAgentExecution(projectId).getId(),
				candidate.getId(),
				"website-qa-full-release@1.0.0",
				"preview-42",
				"website-qa-tools@1.0.0"));

		QaInputSnapshot inputSnapshot =
				qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{\"qaExecutionRef\": \"fixture\"}"));

		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));

		// QaResult and its children mutually reference each other by id (see QaResult's own
		// javadoc) - every child object is constructed in memory first (which is exactly where
		// each one's own id is generated) so their real ids are known before QaResult is built,
		// then QaResult is saved before any child row is, satisfying the children's own foreign
		// keys back to it.
		UUID qaResultId = UUID.randomUUID();
		CandidateFinding finding = new CandidateFinding(
				qaResultId,
				qaExecution.getId(),
				candidate.getId(),
				"NAV_PRIMARY_FLOW_BROKEN",
				"NAVIGATION",
				"MAJOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]",
				"The primary mobile navigation cannot be closed at the required narrow viewport.",
				null,
				"{\"route\":\"/\",\"viewportRef\":\"FULL_NARROW\"}",
				"[\"evidence-1\"]",
				"fingerprint-1",
				"{\"detectionMethod\":\"SEMANTIC\"}");

		AuthorityIssue authorityIssue = new AuthorityIssue(
				qaResultId,
				qaExecution.getId(),
				candidate.getId(),
				"MISSING_INTEGRATION_AUTHORITY",
				"No authorized Integration Contract exists for the bound payment capability.",
				"[\"integration-contract-ref-1\"]",
				"[\"INTEGRATION_CONTRACT\"]",
				"[\"INTEGRATION_BEHAVIOR\"]",
				"[]",
				"{\"detectionMethod\":\"DETERMINISTIC\"}");

		EvaluationIssue evaluationIssue = new EvaluationIssue(
				qaResultId,
				qaExecution.getId(),
				candidate.getId(),
				"TOOL_FAILURE",
				"The accessibility scanner tool failed to run.",
				"[\"ACCESSIBILITY_BASELINE\"]",
				null,
				"accessibility-scanner",
				null,
				"[]",
				"{\"source\":\"TOOL\"}");

		PolicyEvaluation policyEvaluation = new PolicyEvaluation(
				qaResultId,
				qaExecution.getId(),
				candidate.getId(),
				"website-qa-full-release@1.0.0",
				"CANDIDATE_FINDING",
				finding.getId().toString(),
				"severity-default",
				"BLOCK",
				null);

		QaResult qaResult = qaResultRepository.saveAndFlush(new QaResult(
				qaResultId,
				qaExecution.getId(),
				candidate.getId(),
				"website-qa-full-release@1.0.0",
				inputSnapshot.getId(),
				"COMPLETE",
				"[{\"domain\":\"NAVIGATION\",\"applicability\":\"APPLICABLE\"}]",
				"[\"" + finding.getId() + "\"]",
				"[\"" + authorityIssue.getId() + "\"]",
				"[\"" + evaluationIssue.getId() + "\"]",
				"[\"" + policyEvaluation.getId() + "\"]",
				"[]",
				"HOLD",
				"[\"BLOCKING_CANDIDATE_FINDING\"]",
				evidenceManifest.getId(),
				"{\"qaSystemVersion\":\"1.0.0\"}"));

		// Only now that the QaResult row exists can the children satisfy their own FK back to it.
		// The self-assigned, already-non-null id means Spring Data treats save() as a merge, not a
		// persist - it returns a distinct managed copy rather than mutating the object passed in
		// (so @PrePersist-populated fields like createdAt only ever land on the returned
		// reference), which is why every save below is reassigned rather than fire-and-forget.
		finding = candidateFindingRepository.saveAndFlush(finding);
		authorityIssue = authorityIssueRepository.saveAndFlush(authorityIssue);
		evaluationIssue = evaluationIssueRepository.saveAndFlush(evaluationIssue);
		policyEvaluation = policyEvaluationRepository.saveAndFlush(policyEvaluation);

		assertThat(qaResult.getId()).isEqualTo(qaResultId);
		assertThat(finding.getQaResultId()).isEqualTo(qaResultId);
		assertThat(qaResult.getTestedCandidateId()).isEqualTo(candidate.getId());
		assertThat(qaResult.getQaExecutionId()).isEqualTo(qaExecution.getId());
		assertThat(qaResult.getInputSnapshotId()).isEqualTo(inputSnapshot.getId());
		assertThat(qaResult.getEvidenceManifestId()).isEqualTo(evidenceManifest.getId());

		// "References Findings/Issues/Policy Evaluations rather than duplicating authoritative
		// records" - the ref JSON contains only the id string, never the Finding's own content.
		assertThat(qaResult.getFindingRefsJson())
				.contains(finding.getId().toString())
				.doesNotContain("NAV_PRIMARY_FLOW_BROKEN");
		assertThat(qaResult.getAuthorityIssueRefsJson()).contains(authorityIssue.getId().toString());
		assertThat(qaResult.getEvaluationIssueRefsJson()).contains(evaluationIssue.getId().toString());
		assertThat(qaResult.getPolicyEvaluationRefsJson()).contains(policyEvaluation.getId().toString());

		assertThat(candidateFindingRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId()))
				.extracting(CandidateFinding::getId)
				.containsExactly(finding.getId());
		assertThat(authorityIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId()))
				.extracting(AuthorityIssue::getId)
				.containsExactly(authorityIssue.getId());
		assertThat(evaluationIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId()))
				.extracting(EvaluationIssue::getId)
				.containsExactly(evaluationIssue.getId());
		assertThat(policyEvaluationRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId()))
				.extracting(PolicyEvaluation::getId)
				.containsExactly(policyEvaluation.getId());
	}

	@Test
	void aReQaCycleRelatesAnOldFindingToANewCandidateWithoutMutatingTheOldFindingAtAll() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate originalCandidate = seedCandidate(projectId, "prop-a");
		QaExecution originalExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				seedAgentExecution(projectId).getId(),
				originalCandidate.getId(),
				"website-qa-full-release@1.0.0",
				"preview-1",
				"website-qa-tools@1.0.0"));
		UUID originalQaResultId = UUID.randomUUID();
		CandidateFinding originalFinding = new CandidateFinding(
				originalQaResultId,
				originalExecution.getId(),
				originalCandidate.getId(),
				"NAV_PRIMARY_FLOW_BROKEN",
				"NAVIGATION",
				"MAJOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]",
				"The primary mobile navigation cannot be closed.",
				null,
				null,
				"[\"evidence-1\"]",
				"fingerprint-1",
				"{\"detectionMethod\":\"SEMANTIC\"}");
		qaResultRepository.saveAndFlush(minimalQaResult(originalQaResultId, originalExecution, originalCandidate, originalFinding));
		originalFinding = candidateFindingRepository.saveAndFlush(originalFinding);

		// Re-QA runs against a brand-new, independently accepted Candidate (a real QA_REMEDIATION
		// cycle would have gone through Developer + full Technical Verification in between -
		// simulated here with a second fixture Candidate, the same "drive to the state a real
		// pipeline would produce" idiom already used throughout this epic's other IT suites).
		WebsiteImplementationCandidate remediatedCandidate = seedCandidate(projectId, "prop-a");
		QaExecution remediationExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				seedAgentExecution(projectId).getId(),
				remediatedCandidate.getId(),
				"website-qa-full-release@1.0.0",
				"preview-2",
				"website-qa-tools@1.0.0"));
		UUID remediationQaResultId = UUID.randomUUID();
		qaResultRepository.saveAndFlush(
				minimalQaResult(remediationQaResultId, remediationExecution, remediatedCandidate, originalFinding));

		RemediationAssessment assessment = remediationAssessmentRepository.saveAndFlush(new RemediationAssessment(
				originalFinding.getId(),
				remediatedCandidate.getId(),
				remediationExecution.getId(),
				remediationQaResultId,
				"RESOLVED",
				"[\"evidence-2\"]",
				"[]",
				"[]",
				null,
				"{\"assessmentMethod\":\"SEMANTIC\"}"));

		assertThat(assessment.getPreviousFindingId()).isEqualTo(originalFinding.getId());
		assertThat(assessment.getTestedCandidateId()).isEqualTo(remediatedCandidate.getId());
		assertThat(assessment.getStatus()).isEqualTo("RESOLVED");

		// The original Finding is completely untouched - same content, still bound to the
		// original Candidate, never mutated to reflect the later RESOLVED assessment.
		CandidateFinding reloadedOriginalFinding =
				candidateFindingRepository.findById(originalFinding.getId()).orElseThrow();
		assertThat(reloadedOriginalFinding.getTestedCandidateId()).isEqualTo(originalCandidate.getId());
		assertThat(reloadedOriginalFinding.getSummary()).isEqualTo(originalFinding.getSummary());
		assertThat(reloadedOriginalFinding.getCreatedAt()).isEqualTo(originalFinding.getCreatedAt());

		assertThat(remediationAssessmentRepository.findByPreviousFindingIdOrderByCreatedAtAsc(originalFinding.getId()))
				.extracting(RemediationAssessment::getId)
				.containsExactly(assessment.getId());
	}

	@Test
	void multipleQaExecutionsAgainstTheSameCandidateAreAllIndependentlyRetained() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, "prop-a");

		QaExecution first = qaExecutionRepository.saveAndFlush(new QaExecution(
				seedAgentExecution(projectId).getId(), candidate.getId(), "website-qa-comparison-readiness@1.0.0", null, "website-qa-tools@1.0.0"));
		QaExecution second = qaExecutionRepository.saveAndFlush(new QaExecution(
				seedAgentExecution(projectId).getId(), candidate.getId(), "website-qa-full-release@1.0.0", null, "website-qa-tools@1.0.0"));

		List<QaExecution> executions = qaExecutionRepository.findByTestedCandidateIdOrderByCreatedAtAsc(candidate.getId());

		assertThat(executions).extracting(QaExecution::getId).containsExactly(first.getId(), second.getId());
		assertThat(executions).extracting(QaExecution::getQaProfileRef)
				.containsExactly("website-qa-comparison-readiness@1.0.0", "website-qa-full-release@1.0.0");
	}

	private UUID seedProject() {
		return projectRepository.saveAndFlush(new Project("website")).getId();
	}

	private AgentExecution seedAgentExecution(UUID projectId) {
		AgentExecution execution = new AgentExecution(projectId, "website-qa-agent", 1);
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	/** A minimal, otherwise-real QaResult referencing exactly one Finding - just enough to satisfy the foreign keys the remediation test needs. */
	private QaResult minimalQaResult(
			UUID id, QaExecution execution, WebsiteImplementationCandidate candidate, CandidateFinding finding) {
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(execution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(execution.getId()));
		return new QaResult(
				id,
				execution.getId(),
				candidate.getId(),
				execution.getQaProfileRef(),
				inputSnapshot.getId(),
				"COMPLETE",
				"[]",
				"[\"" + finding.getId() + "\"]",
				"[]",
				"[]",
				"[]",
				"[]",
				"HOLD",
				"[\"BLOCKING_CANDIDATE_FINDING\"]",
				evidenceManifest.getId(),
				"{\"qaSystemVersion\":\"1.0.0\"}");
	}

	private WebsiteImplementationCandidate seedCandidate(UUID projectId, String proposalLocalRef) {
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				"design-v1",
				proposalLocalRef,
				"runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				"[]",
				"[]"));
	}
}
