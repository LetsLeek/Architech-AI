package ai.architech.backend.core.qa.policy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.PolicyEvaluation;
import ai.architech.backend.core.qa.PolicyEvaluationRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.qa.profiles.AuthorityIssuePolicy;
import ai.architech.backend.core.qa.profiles.EvaluationIssuePolicy;
import ai.architech.backend.core.qa.profiles.FindingDispositionPolicy;
import ai.architech.backend.core.qa.profiles.QaProfile;
import ai.architech.backend.core.qa.profiles.QaProfileType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof that {@link QAPolicyAggregator}'s returned {@link PolicyEvaluation} rows
 * genuinely satisfy {@code policy_evaluation.qa_result_id}'s real foreign key once inserted in
 * the correct order (AIW-176): aggregate first (in-memory only), build and persist {@code
 * QaResult} using the aggregation's own {@code gateOutcome}/{@code holdReasons}, then persist the
 * returned {@code PolicyEvaluation} rows.
 */
@SpringBootTest
@Transactional
class QAPolicyAggregatorPersistenceIT {

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
	private QaResultRepository qaResultRepository;

	@Autowired
	private PolicyEvaluationRepository policyEvaluationRepository;

	@Autowired
	private QAPolicyAggregator aggregator;

	@Test
	void theAggregatorsOutputPolicyEvaluationsPersistOnceTheirQaResultExists() {
		UUID projectId = projectRepository.saveAndFlush(new Project("website")).getId();
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), "design-v1", "prop-a", "runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));

		AgentExecution qaAgentExecution = new AgentExecution(projectId, "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", null, "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));

		UUID qaResultId = UUID.randomUUID();
		CandidateFinding finding = new CandidateFinding(
				qaResultId, qaExecution.getId(), candidate.getId(), "NAV_TARGET_MISMATCH", "NAVIGATION", "MINOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-1", "{\"detectionMethod\":\"SEMANTIC\"}");

		QaPolicyAggregationResult aggregation = aggregator.aggregate(
				qaResultId, qaExecution.getId(), candidate.getId(), fullReleaseProfile(), QaEvaluationState.COMPLETE,
				List.of(finding), List.of(), List.of(), true, false);

		QaResult qaResult = qaResultRepository.saveAndFlush(new QaResult(
				qaResultId, qaExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", inputSnapshot.getId(),
				"COMPLETE", "[]", "[]", "[]", "[]", "[]", "[]", aggregation.gateOutcome().name(),
				"[" + aggregation.holdReasons().stream().map(r -> "\"" + r + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]",
				evidenceManifest.getId(), "{\"qaSystemVersion\":\"1.0.0\"}"));

		for (PolicyEvaluation policyEvaluation : aggregation.policyEvaluations()) {
			policyEvaluationRepository.saveAndFlush(policyEvaluation);
		}

		assertThat(qaResult.getGateOutcome()).isEqualTo("PASS");
		assertThat(policyEvaluationRepository.findByQaResultIdOrderByCreatedAtAsc(qaResultId))
				.extracting(PolicyEvaluation::getDisposition)
				.containsExactly("ALLOW");
	}

	private QaProfile fullReleaseProfile() {
		return new QaProfile(
				"website-qa-full-release@1.0.0", QaProfileType.FULL_RELEASE, List.of(), List.of(), List.of(),
				new FindingDispositionPolicy(Map.of(), Map.of(
						ai.architech.backend.core.qa.invariants.QaSeverity.CRITICAL, PolicyDisposition.BLOCK,
						ai.architech.backend.core.qa.invariants.QaSeverity.MAJOR, PolicyDisposition.BLOCK,
						ai.architech.backend.core.qa.invariants.QaSeverity.MINOR, PolicyDisposition.ALLOW)),
				Optional.empty(),
				new AuthorityIssuePolicy(PolicyDisposition.ESCALATE),
				new EvaluationIssuePolicy(PolicyDisposition.ESCALATE));
	}
}
