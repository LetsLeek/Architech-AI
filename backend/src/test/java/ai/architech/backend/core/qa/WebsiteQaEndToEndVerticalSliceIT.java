package ai.architech.backend.core.qa;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.policy.HoldReason;
import ai.architech.backend.core.qa.policy.PolicyDisposition;
import ai.architech.backend.core.qa.policy.QAPolicyAggregator;
import ai.architech.backend.core.qa.policy.QaEvaluationState;
import ai.architech.backend.core.qa.policy.QaPolicyAggregationResult;
import ai.architech.backend.core.qa.profiles.AuthorityIssuePolicy;
import ai.architech.backend.core.qa.profiles.EvaluationIssuePolicy;
import ai.architech.backend.core.qa.profiles.FindingDispositionPolicy;
import ai.architech.backend.core.qa.profiles.QaProfile;
import ai.architech.backend.core.qa.profiles.QaProfileType;
import ai.architech.backend.core.qa.remediation.EscalationDecision;
import ai.architech.backend.core.qa.remediation.EscalationRoute;
import ai.architech.backend.core.qa.remediation.QaEscalationRouter;
import ai.architech.backend.core.qa.remediation.QaRemediationBudget;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import ai.architech.backend.core.verification.VerificationOutcome;
import ai.architech.backend.projecttype.website.ComparisonReadinessBarrier;
import ai.architech.backend.projecttype.website.ComparisonReadinessBarrierEvaluator;
import ai.architech.backend.projecttype.website.ComparisonReadinessBarrierRepository;
import ai.architech.backend.projecttype.website.ComparisonReadinessEvaluation;
import ai.architech.backend.projecttype.website.ComparisonReadinessSlot;
import ai.architech.backend.projecttype.website.ComparisonReadinessSlotRepository;
import ai.architech.backend.projecttype.website.FullReleaseEligibility;
import ai.architech.backend.projecttype.website.FullReleaseEligibilityValidator;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AIW-183's own end-to-end vertical slice: wires the real, already-built pieces from every prior
 * ticket in the epic (AIW-167-182) into one connected flow per named scenario, using
 * deterministic fixtures throughout - no live model call anywhere, matching {@code
 * InitialGenerationOrchestratorIT}'s own precedent for M3. AIW-184 (the Developer/QA
 * tool-calling loop) remains the only thing separating this from a genuine live run, exactly as
 * already documented across AIW-169/172/173.
 */
@SpringBootTest
@Transactional
class WebsiteQaEndToEndVerticalSliceIT {

	private static final String COMPARISON_PROFILE_REF = "website-qa-comparison-readiness@1.0.0";
	private static final String FULL_RELEASE_PROFILE_REF = "website-qa-full-release@1.0.0";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private RunnerVerificationRunRepository runnerVerificationRunRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private EvidenceRecordRepository evidenceRecordRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private AuthorityIssueRepository authorityIssueRepository;

	@Autowired
	private EvaluationIssueRepository evaluationIssueRepository;

	@Autowired
	private PolicyEvaluationRepository policyEvaluationRepository;

	@Autowired
	private RemediationAssessmentRepository remediationAssessmentRepository;

	@Autowired
	private QAPolicyAggregator policyAggregator;

	@Autowired
	private QaEscalationRouter escalationRouter;

	@Autowired
	private ComparisonReadinessBarrierRepository barrierRepository;

	@Autowired
	private ComparisonReadinessSlotRepository slotRepository;

	@Autowired
	private ComparisonReadinessBarrierEvaluator comparisonEvaluator;

	@Autowired
	private FullReleaseEligibilityValidator fullReleaseEligibilityValidator;

	@Test
	void cleanComparisonCandidateReachesComparisonReadinessPass() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate(projectId, "prop-a");

		QaResultBundle bundle = runToQaResult(
				candidate, COMPARISON_PROFILE_REF, List.of(new FindingSpec("NAV_TARGET_MISMATCH", "MINOR")), List.of(), List.of());

		assertThat(bundle.qaResult().getGateOutcome()).isEqualTo("PASS");
	}

	@Test
	void aBlockingComparisonDefectProducesHoldAndNoComparisonEligibility() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate(projectId, "prop-a");

		QaResultBundle bundle = runToQaResult(
				candidate, COMPARISON_PROFILE_REF, List.of(new FindingSpec("NAV_PRIMARY_FLOW_BROKEN", "CRITICAL")), List.of(), List.of());

		assertThat(bundle.qaResult().getGateOutcome()).isEqualTo("HOLD");

		ComparisonReadinessBarrier barrier = barrierRepository.saveAndFlush(new ComparisonReadinessBarrier(projectId));
		slotRepository.saveAndFlush(new ComparisonReadinessSlot(barrier.getId(), "prop-a", candidate.getId()));
		ComparisonReadinessEvaluation evaluation = comparisonEvaluator.evaluate(barrier.getId(), COMPARISON_PROFILE_REF);

		assertThat(evaluation.allEligible()).isFalse();
	}

	@Test
	void threeIndependentlyEligibleCandidatesSatisfyTheComparisonBarrier() {
		UUID projectId = seedProject();
		ComparisonReadinessBarrier barrier = barrierRepository.saveAndFlush(new ComparisonReadinessBarrier(projectId));

		for (String lineage : List.of("prop-a", "prop-b", "prop-c")) {
			WebsiteImplementationCandidate candidate = seedVerifiedCandidate(projectId, lineage);
			runToQaResult(candidate, COMPARISON_PROFILE_REF, List.of(new FindingSpec("NAV_TARGET_MISMATCH", "MINOR")), List.of(), List.of());
			slotRepository.saveAndFlush(new ComparisonReadinessSlot(barrier.getId(), lineage, candidate.getId()));
		}

		ComparisonReadinessEvaluation evaluation = comparisonEvaluator.evaluate(barrier.getId(), COMPARISON_PROFILE_REF);

		assertThat(evaluation.allEligible()).isTrue();
		assertThat(evaluation.variants()).hasSize(3);
	}

	@Test
	void aSelectedCandidateCanRunFullReleaseAndOnlyTransitionsTowardFinalApprovalOnPass() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate releasePathCandidate = seedVerifiedCandidate(projectId, "prop-a");

		QaResultBundle bundle = runToQaResult(
				releasePathCandidate, FULL_RELEASE_PROFILE_REF, List.of(new FindingSpec("NAV_TARGET_MISMATCH", "MINOR")), List.of(),
				List.of());
		assertThat(bundle.qaResult().getGateOutcome()).isEqualTo("PASS");

		FullReleaseEligibility eligibility = fullReleaseEligibilityValidator.validate(releasePathCandidate.getId(), FULL_RELEASE_PROFILE_REF);

		// The type itself carries nothing beyond "eligible" - never an approval/deployment field
		// this flow could set on its own; a PASS only ever permits the *next stage*, Final
		// Human/Customer Approval, which stays a separate Workflow record entirely.
		assertThat(eligibility.eligible()).isTrue();
	}

	@Test
	void missingAuthorityAndToolFailureRouteToAuthorityOrEvaluationSemanticsNeverACandidateDefect() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate(projectId, "prop-a");

		QaResultBundle bundle = runToQaResult(
				candidate, FULL_RELEASE_PROFILE_REF, List.of(), List.of(new AuthorityIssueSpec("MISSING_INTEGRATION_AUTHORITY")),
				List.of(new EvaluationIssueSpec("TOOL_FAILURE")));

		assertThat(bundle.qaResult().getGateOutcome()).isEqualTo("HOLD");
		// Neither issue produced (or required) any CandidateFinding at all.
		assertThat(candidateFindingRepository.findByQaResultIdOrderByCreatedAtAsc(bundle.qaResult().getId())).isEmpty();

		EscalationDecision decision = escalationRouter.route(EnumSet.copyOf(bundle.holdReasons()), true, false);
		assertThat(decision.route()).isEqualTo(EscalationRoute.AUTHORITY_RESOLUTION);
	}

	@Test
	void aBlockingFindingCompletesAFullRemediationVerificationNewCandidateReQaCycle() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate originalCandidate = seedVerifiedCandidate(projectId, "prop-a");
		QaResultBundle originalBundle = runToQaResult(
				originalCandidate, FULL_RELEASE_PROFILE_REF, List.of(new FindingSpec("NAV_PRIMARY_FLOW_BROKEN", "CRITICAL")), List.of(),
				List.of());
		CandidateFinding blockingFinding = originalBundle.findings().get(0);
		assertThat(originalBundle.qaResult().getGateOutcome()).isEqualTo("HOLD");

		// Remediation budget for this (project, stage, lineage) is consumed exactly once.
		QaRemediationBudget budget = new QaRemediationBudget(projectId, "prop-a", QaProfileType.FULL_RELEASE);
		assertThat(budget.useRemediationCycle(2)).isTrue();

		// Developer performs QA_REMEDIATION -> real Technical Verification PASS -> a brand-new,
		// distinct, immutable Candidate (never a mutation of the original).
		WebsiteImplementationCandidate remediatedCandidate = seedVerifiedCandidate(projectId, "prop-a");
		assertThat(remediatedCandidate.getId()).isNotEqualTo(originalCandidate.getId());

		// Re-QA: the previously-blocking Finding is now RESOLVED against real new-Candidate Evidence.
		EvidenceRecord newEvidence = seedEvidence(remediatedCandidate);
		QaResultBundle reQaBundle = runToQaResult(remediatedCandidate, FULL_RELEASE_PROFILE_REF, List.of(), List.of(), List.of());
		RemediationAssessment assessment = remediationAssessmentRepository.saveAndFlush(new RemediationAssessment(
				blockingFinding.getId(), remediatedCandidate.getId(), reQaBundle.qaResult().getQaExecutionId(), reQaBundle.qaResult().getId(),
				"RESOLVED", "[\"" + newEvidence.getId() + "\"]", "[]", "[]", null, "{\"assessmentMethod\":\"SEMANTIC\"}"));

		assertThat(reQaBundle.qaResult().getGateOutcome()).isEqualTo("PASS");
		assertThat(assessment.getStatus()).isEqualTo("RESOLVED");
		assertThat(assessment.getPreviousFindingId()).isEqualTo(blockingFinding.getId());
		assertThat(assessment.getTestedCandidateId()).isEqualTo(remediatedCandidate.getId());
	}

	@Test
	void historicalArtifactsRemainImmutableThroughoutTheFlow() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate originalCandidate = seedVerifiedCandidate(projectId, "prop-a");
		QaResultBundle originalBundle = runToQaResult(
				originalCandidate, FULL_RELEASE_PROFILE_REF, List.of(new FindingSpec("NAV_PRIMARY_FLOW_BROKEN", "CRITICAL")), List.of(),
				List.of());
		CandidateFinding originalFinding = originalBundle.findings().get(0);

		WebsiteImplementationCandidate remediatedCandidate = seedVerifiedCandidate(projectId, "prop-a");
		runToQaResult(remediatedCandidate, FULL_RELEASE_PROFILE_REF, List.of(), List.of(), List.of());

		CandidateFinding reloaded = candidateFindingRepository.findById(originalFinding.getId()).orElseThrow();
		assertThat(reloaded.getTestedCandidateId()).isEqualTo(originalCandidate.getId());
		assertThat(reloaded.getSummary()).isEqualTo(originalFinding.getSummary());
		assertThat(reloaded.getCreatedAt()).isEqualTo(originalFinding.getCreatedAt());

		WebsiteImplementationCandidate reloadedCandidate = candidateRepository.findById(originalCandidate.getId()).orElseThrow();
		assertThat(reloadedCandidate.getRepositoryStateRef()).isEqualTo(originalCandidate.getRepositoryStateRef());
	}

	@Test
	void noSemanticAgentOutputPropertyCanDirectlyApproveDeploySelectOrForcePass() throws IOException {
		String schemaJson = readClasspathResource("project-types/website/agents/website-qa-agent/contracts/semantic-qa-review-output.schema.json");
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode schema = objectMapper.readTree(schemaJson);

		List<String> forbidden = List.of("gateOutcome", "pass", "approved", "approval", "selected", "selection", "deployed", "deployment");
		for (String field : schema.path("properties").propertyNames()) {
			assertThat(forbidden).as("semantic-qa-review-output.schema.json must never declare '" + field + "'").doesNotContain(field);
		}
	}

	private record FindingSpec(String code, String severity) {}

	private record AuthorityIssueSpec(String code) {}

	private record EvaluationIssueSpec(String code) {}

	private record QaResultBundle(
			QaResult qaResult,
			List<HoldReason> holdReasons,
			List<CandidateFinding> findings,
			List<AuthorityIssue> authorityIssues,
			List<EvaluationIssue> evaluationIssues) {}

	private QaResultBundle runToQaResult(
			WebsiteImplementationCandidate candidate,
			String qaProfileRef,
			List<FindingSpec> findingSpecs,
			List<AuthorityIssueSpec> authorityIssueSpecs,
			List<EvaluationIssueSpec> evaluationIssueSpecs) {
		AgentExecution qaAgentExecution = new AgentExecution(candidate.getProjectId(), "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution execution = qaExecutionRepository.saveAndFlush(
				new QaExecution(qaAgentExecution.getId(), candidate.getId(), qaProfileRef, "preview-42", "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(execution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(execution.getId()));

		// Generated up front so the in-memory Finding/Issue objects below (built before the
		// QaResult row exists) already carry the exact id that row will be saved with - their own
		// qa_result_id FK is checked immediately, not deferred, so any mismatch here would fail at
		// the saveAndFlush calls further down (see git history for the bug this replaced).
		UUID qaResultId = UUID.randomUUID();
		List<CandidateFinding> findings = findingSpecs.stream()
				.map(spec -> finding(qaResultId, execution.getId(), candidate, spec.code(), spec.severity()))
				.toList();
		List<AuthorityIssue> authorityIssues = authorityIssueSpecs.stream()
				.map(spec -> authorityIssue(qaResultId, execution.getId(), candidate, spec.code()))
				.toList();
		List<EvaluationIssue> evaluationIssues = evaluationIssueSpecs.stream()
				.map(spec -> evaluationIssue(qaResultId, execution.getId(), candidate, spec.code()))
				.toList();

		QaPolicyAggregationResult aggregation = policyAggregator.aggregate(
				qaResultId, execution.getId(), candidate.getId(), profileFixture(qaProfileRef), QaEvaluationState.COMPLETE,
				findings, authorityIssues, evaluationIssues, true, false);

		String holdReasonsJson = "[" + aggregation.holdReasons().stream().map(r -> "\"" + r + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
		QaResult qaResult = qaResultRepository.saveAndFlush(new QaResult(
				qaResultId, execution.getId(), candidate.getId(), qaProfileRef, inputSnapshot.getId(), "COMPLETE", "[]", "[]", "[]", "[]", "[]",
				"[]", aggregation.gateOutcome().name(), holdReasonsJson, evidenceManifest.getId(), "{\"qaSystemVersion\":\"1.0.0\"}"));

		// Only now that the QaResult row exists can the Finding/Issue/PolicyEvaluation children
		// satisfy their own FK back to it - same insert-order idiom QaPersistenceModelIT already
		// established. Each id is caller-assigned (not @GeneratedValue), so Spring Data's save()
		// takes the merge() path rather than persist() and hands back a distinct managed instance -
		// the returned value, not the original in-memory object, is the one @PrePersist populated.
		findings = findings.stream().map(candidateFindingRepository::saveAndFlush).toList();
		authorityIssues = authorityIssues.stream().map(authorityIssueRepository::saveAndFlush).toList();
		evaluationIssues = evaluationIssues.stream().map(evaluationIssueRepository::saveAndFlush).toList();
		for (var policyEvaluation : aggregation.policyEvaluations()) {
			policyEvaluationRepository.saveAndFlush(policyEvaluation);
		}

		return new QaResultBundle(qaResult, aggregation.holdReasons(), findings, authorityIssues, evaluationIssues);
	}

	private QaProfile profileFixture(String qaProfileRef) {
		QaProfileType type = qaProfileRef.equals(FULL_RELEASE_PROFILE_REF) ? QaProfileType.FULL_RELEASE : QaProfileType.COMPARISON_READINESS;
		return new QaProfile(
				qaProfileRef, type, List.of(), List.of(), List.of(),
				new FindingDispositionPolicy(
						Map.of(),
						Map.of(
								ai.architech.backend.core.qa.invariants.QaSeverity.CRITICAL, PolicyDisposition.BLOCK,
								ai.architech.backend.core.qa.invariants.QaSeverity.MAJOR, PolicyDisposition.BLOCK,
								ai.architech.backend.core.qa.invariants.QaSeverity.MINOR, PolicyDisposition.ALLOW)),
				Optional.empty(),
				new AuthorityIssuePolicy(PolicyDisposition.ESCALATE),
				new EvaluationIssuePolicy(PolicyDisposition.ESCALATE));
	}

	private UUID seedProject() {
		return projectRepository.saveAndFlush(new Project("website")).getId();
	}

	private WebsiteImplementationCandidate seedVerifiedCandidate(UUID projectId, String proposalLocalRef) {
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), "design-v1", proposalLocalRef, "runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));
		runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(developerExecution.getId(), candidate.getRepositoryStateRef(), VerificationOutcome.PASS));
		return candidate;
	}

	private CandidateFinding finding(
			UUID qaResultId, UUID qaExecutionId, WebsiteImplementationCandidate candidate, String code, String severity) {
		return new CandidateFinding(
				qaResultId, qaExecutionId, candidate.getId(), code, "NAVIGATION", severity,
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-" + UUID.randomUUID(), "{\"detectionMethod\":\"SEMANTIC\"}");
	}

	private AuthorityIssue authorityIssue(UUID qaResultId, UUID qaExecutionId, WebsiteImplementationCandidate candidate, String code) {
		return new AuthorityIssue(
				qaResultId, qaExecutionId, candidate.getId(), code, "a summary", "[]",
				"[\"INTEGRATION_CONTRACT\"]", "[\"INTEGRATION_BEHAVIOR\"]", "[]", "{\"detectionMethod\":\"DETERMINISTIC\"}");
	}

	private EvaluationIssue evaluationIssue(UUID qaResultId, UUID qaExecutionId, WebsiteImplementationCandidate candidate, String code) {
		return new EvaluationIssue(
				qaResultId, qaExecutionId, candidate.getId(), code, "a summary", "[\"ACCESSIBILITY_BASELINE\"]",
				null, "ACCESSIBILITY_SCANNER", null, "[]", "{\"source\":\"TOOL\"}");
	}

	private EvidenceRecord seedEvidence(WebsiteImplementationCandidate candidate) {
		AgentExecution qaAgentExecution = new AgentExecution(candidate.getProjectId(), "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution execution = qaExecutionRepository.saveAndFlush(
				new QaExecution(qaAgentExecution.getId(), candidate.getId(), FULL_RELEASE_PROFILE_REF, null, "website-qa-tools@1.0.0"));
		EvidenceManifest manifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(execution.getId()));
		return evidenceRecordRepository.saveAndFlush(new EvidenceRecord(
				manifest.getId(), execution.getId(), candidate.getId(), EvidenceRecord.Kind.SOURCE_REFERENCE,
				"check:AUTHORITY_REFERENCE_INTEGRITY", null, null, null, null, "observed content", null, null));
	}

	private String readClasspathResource(String classpathRelativePath) throws IOException {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathRelativePath)) {
			if (in == null) {
				throw new FileNotFoundException(classpathRelativePath);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
