package ai.architech.backend.core.qa.policy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.qa.AuthorityIssue;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.EvaluationIssue;
import ai.architech.backend.core.qa.invariants.QaSeverity;
import ai.architech.backend.core.qa.profiles.AuthorityIssuePolicy;
import ai.architech.backend.core.qa.profiles.EvaluationIssuePolicy;
import ai.architech.backend.core.qa.profiles.FindingDispositionPolicy;
import ai.architech.backend.core.qa.profiles.QaProfile;
import ai.architech.backend.core.qa.profiles.QaProfileType;
import ai.architech.backend.core.qa.profiles.RequirementPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure, deterministic coverage of {@link QAPolicyAggregator} (AIW-176) - no Postgres, no model call, matching "no generative AI". */
class QAPolicyAggregatorTests {

	private final QAPolicyAggregator aggregator = new QAPolicyAggregator();
	private final UUID qaResultId = UUID.randomUUID();
	private final UUID qaExecutionId = UUID.randomUUID();
	private final UUID testedCandidateId = UUID.randomUUID();

	@Test
	void anExactCodeOverrideWinsOverTheSeverityDefault() {
		// CONTENT_PLACEHOLDER_LEAK is MINOR (severity-default ALLOW) but full-release's own
		// explicitCodeRules force BLOCK regardless.
		CandidateFinding finding = finding("CONTENT_PLACEHOLDER_LEAK", "MINOR");
		QaProfile profile = fullReleaseProfile();

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, profile, QaEvaluationState.COMPLETE,
						List.of(finding), List.of(), List.of(), true, false);

		assertThat(result.policyEvaluations()).hasSize(1);
		assertThat(result.policyEvaluations().get(0).getDisposition()).isEqualTo("BLOCK");
		assertThat(result.policyEvaluations().get(0).getPolicyRuleRef()).isEqualTo("exact-code:CONTENT_PLACEHOLDER_LEAK");
		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
	}

	@Test
	void theRequirementSpecificRuleAppliesToReqMustUnfulfilledOnFullRelease() {
		CandidateFinding finding = finding("REQ_MUST_UNFULFILLED", "MAJOR");
		QaProfile profile = fullReleaseProfile();

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, profile, QaEvaluationState.COMPLETE,
						List.of(finding), List.of(), List.of(), true, false);

		assertThat(result.policyEvaluations().get(0).getDisposition()).isEqualTo("BLOCK");
		assertThat(result.policyEvaluations().get(0).getPolicyRuleRef()).isEqualTo("requirement-specific:materiallyUnfulfilledMust");
	}

	@Test
	void reqMustUnfulfilledFallsToSeverityDefaultOnComparisonReadinessWhichHasNoRequirementPolicy() {
		CandidateFinding finding = finding("REQ_MUST_UNFULFILLED", "MAJOR");
		QaProfile profile = comparisonReadinessProfile();

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, profile, QaEvaluationState.COMPLETE,
						List.of(finding), List.of(), List.of(), true, false);

		assertThat(result.policyEvaluations().get(0).getPolicyRuleRef()).isEqualTo("severity-default");
		assertThat(result.policyEvaluations().get(0).getDisposition()).isEqualTo("BLOCK"); // severityDefaults.MAJOR = BLOCK
	}

	@Test
	void aFindingWithNoSpecialRuleFallsToItsSeverityDefault() {
		CandidateFinding minorFinding = finding("NAV_TARGET_MISMATCH", "MINOR");
		QaProfile profile = fullReleaseProfile();

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, profile, QaEvaluationState.COMPLETE,
						List.of(minorFinding), List.of(), List.of(), true, false);

		assertThat(result.policyEvaluations().get(0).getDisposition()).isEqualTo("ALLOW");
		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.PASS);
	}

	@Test
	void anAuthorityIssueEscalatesAndPreventsPassWithTheCorrectHoldReason() {
		AuthorityIssue authorityIssue = authorityIssue("MISSING_AUTHORITY");
		QaProfile profile = fullReleaseProfile();

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, profile, QaEvaluationState.COMPLETE,
						List.of(), List.of(authorityIssue), List.of(), true, false);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
		assertThat(result.holdReasons()).containsExactly(HoldReason.AUTHORITY_RESOLUTION_REQUIRED);
	}

	@Test
	void anEvaluationIssueEscalatesAndPreventsPassWithTheCorrectHoldReason() {
		EvaluationIssue evaluationIssue = evaluationIssue("TOOL_FAILURE");
		QaProfile profile = fullReleaseProfile();

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, profile, QaEvaluationState.COMPLETE,
						List.of(), List.of(), List.of(evaluationIssue), true, false);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
		assertThat(result.holdReasons()).containsExactly(HoldReason.HUMAN_REVIEW_REQUIRED);
	}

	@Test
	void aPartialEvaluationStateCanNeverProducePass() {
		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, fullReleaseProfile(), QaEvaluationState.PARTIAL,
						List.of(), List.of(), List.of(), true, false);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
		assertThat(result.holdReasons()).containsExactly(HoldReason.EVALUATION_INCOMPLETE);
	}

	@Test
	void anInvalidEvaluationStateCanNeverProducePass() {
		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, fullReleaseProfile(), QaEvaluationState.INVALID,
						List.of(), List.of(), List.of(), true, false);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
		assertThat(result.holdReasons()).containsExactly(HoldReason.EXECUTION_INVALID);
	}

	@Test
	void incompleteRequiredCoveragePreventsPass() {
		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, fullReleaseProfile(), QaEvaluationState.COMPLETE,
						List.of(), List.of(), List.of(), false, false);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
		assertThat(result.holdReasons()).containsExactly(HoldReason.EVALUATION_INCOMPLETE);
	}

	@Test
	void aFullyCleanEvaluationPasses() {
		CandidateFinding minorFinding = finding("NAV_TARGET_MISMATCH", "MINOR");

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, fullReleaseProfile(), QaEvaluationState.COMPLETE,
						List.of(minorFinding), List.of(), List.of(), true, false);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.PASS);
		assertThat(result.holdReasons()).isEmpty();
	}

	@Test
	void materiallyUnfulfilledMustRequirementPreventsFullReleasePassEvenWithoutAMatchingFinding() {
		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, fullReleaseProfile(), QaEvaluationState.COMPLETE,
						List.of(), List.of(), List.of(), true, true);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.HOLD);
		assertThat(result.holdReasons()).containsExactly(HoldReason.BLOCKING_CANDIDATE_FINDING);
	}

	@Test
	void theMateriallyUnfulfilledMustSignalIsIgnoredOnComparisonReadiness() {
		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, comparisonReadinessProfile(), QaEvaluationState.COMPLETE,
						List.of(), List.of(), List.of(), true, true);

		assertThat(result.gateOutcome()).isEqualTo(GateOutcome.PASS);
	}

	@Test
	void everyPolicyEvaluationCarriesTheExactSubjectAndProfileRef() {
		CandidateFinding finding = finding("NAV_TARGET_MISMATCH", "MINOR");
		QaProfile profile = fullReleaseProfile();

		QaPolicyAggregationResult result =
				aggregator.aggregate(qaResultId, qaExecutionId, testedCandidateId, profile, QaEvaluationState.COMPLETE,
						List.of(finding), List.of(), List.of(), true, false);

		assertThat(result.policyEvaluations().get(0).getQaResultId()).isEqualTo(qaResultId);
		assertThat(result.policyEvaluations().get(0).getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(result.policyEvaluations().get(0).getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(result.policyEvaluations().get(0).getQaProfileRef()).isEqualTo(profile.ref());
		assertThat(result.policyEvaluations().get(0).getSubjectType()).isEqualTo("CANDIDATE_FINDING");
		assertThat(result.policyEvaluations().get(0).getSubjectRef()).isEqualTo(finding.getId().toString());
	}

	private CandidateFinding finding(String code, String severity) {
		return new CandidateFinding(
				qaResultId, qaExecutionId, testedCandidateId, code, "NAVIGATION", severity,
				"[{\"type\":\"WEBSITE_REQUIREMENT\",\"ref\":\"req-1\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-" + UUID.randomUUID(), "{\"detectionMethod\":\"SEMANTIC\"}");
	}

	private AuthorityIssue authorityIssue(String code) {
		return new AuthorityIssue(
				qaResultId, qaExecutionId, testedCandidateId, code, "a summary", "[]",
				"[\"INTEGRATION_CONTRACT\"]", "[\"INTEGRATION_BEHAVIOR\"]", "[]", "{\"detectionMethod\":\"DETERMINISTIC\"}");
	}

	private EvaluationIssue evaluationIssue(String code) {
		return new EvaluationIssue(
				qaResultId, qaExecutionId, testedCandidateId, code, "a summary", "[\"ACCESSIBILITY_BASELINE\"]",
				null, "ACCESSIBILITY_SCANNER", null, "[]", "{\"source\":\"TOOL\"}");
	}

	private QaProfile fullReleaseProfile() {
		return new QaProfile(
				"website-qa-full-release@1.0.0", QaProfileType.FULL_RELEASE, List.of(), List.of(), List.of(),
				new FindingDispositionPolicy(
						Map.of("CONTENT_PLACEHOLDER_LEAK", PolicyDisposition.BLOCK),
						Map.of(QaSeverity.CRITICAL, PolicyDisposition.BLOCK, QaSeverity.MAJOR, PolicyDisposition.BLOCK, QaSeverity.MINOR, PolicyDisposition.ALLOW)),
				Optional.of(new RequirementPolicy(PolicyDisposition.BLOCK, false)),
				new AuthorityIssuePolicy(PolicyDisposition.ESCALATE),
				new EvaluationIssuePolicy(PolicyDisposition.ESCALATE));
	}

	private QaProfile comparisonReadinessProfile() {
		return new QaProfile(
				"website-qa-comparison-readiness@1.0.0", QaProfileType.COMPARISON_READINESS, List.of(), List.of(), List.of(),
				new FindingDispositionPolicy(
						Map.of(),
						Map.of(QaSeverity.CRITICAL, PolicyDisposition.BLOCK, QaSeverity.MAJOR, PolicyDisposition.BLOCK, QaSeverity.MINOR, PolicyDisposition.ALLOW)),
				Optional.empty(),
				new AuthorityIssuePolicy(PolicyDisposition.ESCALATE),
				new EvaluationIssuePolicy(PolicyDisposition.ESCALATE));
	}
}
