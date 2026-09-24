package ai.architech.backend.core.qa.policy;

import ai.architech.backend.core.qa.AuthorityIssue;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.EvaluationIssue;
import ai.architech.backend.core.qa.PolicyEvaluation;
import ai.architech.backend.core.qa.invariants.QaSeverity;
import ai.architech.backend.core.qa.profiles.QaProfile;
import ai.architech.backend.core.qa.profiles.QaProfileType;
import ai.architech.backend.core.qa.profiles.RequirementPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@code core/POLICY_AGGREGATOR.md}'s own {@code QAPolicyAggregator} - "MUST NOT use generative
 * AI" (AIW-176). Converts already-validated {@link CandidateFinding}/{@link AuthorityIssue}/{@link
 * EvaluationIssue} rows (AIW-174 already guaranteed these are structurally sound before they were
 * ever persisted) into immutable {@link PolicyEvaluation} records plus a final {@code PASS|HOLD}
 * gate outcome - purely deterministic table lookups against the active {@link QaProfile}'s own
 * disposition policy, never a model call.
 *
 * <p><b>Finding disposition precedence</b> (exactly the three tiers {@code
 * core/POLICY_AGGREGATOR.md} names): (1) {@code findingDispositionPolicy.explicitCodeRules} exact
 * finding-code override; (2) {@code requirementPolicy.materiallyUnfulfilledMust} for the {@code
 * REQ_MUST_UNFULFILLED} finding code specifically - the one code whose own purpose is exactly "a
 * must Requirement was materially unfulfilled," the literal, non-invented reading of "applicable
 * requirement-specific rule" against what the frozen profiles actually declare; (3) {@code
 * findingDispositionPolicy.severityDefaults} fallback.
 *
 * <p><b>Domain coverage and Requirement-traceability computation are deliberately out of this
 * ticket's scope</b> - {@code core/VALIDATORS.md} names {@code DomainResultInvariantValidator} and
 * {@code RequirementCoverageValidator} as their own separate, not-yet-ticketed validators. {@link
 * #aggregate} accepts their outcomes ({@code requiredCoverageComplete}, {@code
 * materiallyUnfulfilledMustRequirementExists}) as caller-supplied signals rather than computing
 * them itself, the same "build the classifier before its real input source exists" boundary {@code
 * CandidateBindingValidator#validateExecutionSurface} already established for AIW-169.
 *
 * <p><b>Deliberately performs no persistence itself.</b> {@code policy_evaluation}'s own {@code
 * qa_result_id} column is a real foreign key into {@code qa_result} - but a {@code QaResult} row
 * cannot exist yet at aggregation time, since its own {@code gateOutcome}/{@code
 * holdReasonsJson} fields are exactly this method's output (the same circular-dependency shape
 * {@code QaResult}'s own javadoc already documents and solves for its Finding/Issue children: the
 * caller must insert {@code QaResult} first using this result's {@link
 * QaPolicyAggregationResult#gateOutcome()}/{@link QaPolicyAggregationResult#holdReasons()}, then
 * persist the returned, already-constructed {@link PolicyEvaluation} rows.
 */
@Component
public class QAPolicyAggregator {

	private static final String REQ_MUST_UNFULFILLED_CODE = "REQ_MUST_UNFULFILLED";
	private static final String SUBJECT_TYPE_FINDING = "CANDIDATE_FINDING";
	private static final String SUBJECT_TYPE_AUTHORITY_ISSUE = "AUTHORITY_ISSUE";
	private static final String SUBJECT_TYPE_EVALUATION_ISSUE = "EVALUATION_ISSUE";

	public QaPolicyAggregationResult aggregate(
			UUID qaResultId,
			UUID qaExecutionId,
			UUID testedCandidateId,
			QaProfile profile,
			QaEvaluationState evaluationState,
			List<CandidateFinding> findings,
			List<AuthorityIssue> authorityIssues,
			List<EvaluationIssue> evaluationIssues,
			boolean requiredCoverageComplete,
			boolean materiallyUnfulfilledMustRequirementExists) {
		List<PolicyEvaluation> policyEvaluations = new ArrayList<>();

		for (CandidateFinding finding : findings) {
			PolicyDisposition disposition = disposeFinding(finding, profile);
			policyEvaluations.add(new PolicyEvaluation(
					qaResultId, qaExecutionId, testedCandidateId, profile.ref(), SUBJECT_TYPE_FINDING,
					finding.getId().toString(), findingPolicyRuleRef(finding, profile), disposition.name(), null));
		}
		for (AuthorityIssue authorityIssue : authorityIssues) {
			PolicyDisposition disposition = profile.authorityIssuePolicy().gateRelevantIssueDisposition();
			policyEvaluations.add(new PolicyEvaluation(
					qaResultId, qaExecutionId, testedCandidateId, profile.ref(), SUBJECT_TYPE_AUTHORITY_ISSUE,
					authorityIssue.getId().toString(), "authority-issue-policy", disposition.name(), null));
		}
		for (EvaluationIssue evaluationIssue : evaluationIssues) {
			PolicyDisposition disposition = profile.evaluationIssuePolicy().requiredEvaluationDisposition();
			policyEvaluations.add(new PolicyEvaluation(
					qaResultId, qaExecutionId, testedCandidateId, profile.ref(), SUBJECT_TYPE_EVALUATION_ISSUE,
					evaluationIssue.getId().toString(), "evaluation-issue-policy", disposition.name(), null));
		}

		boolean fullRelease = profile.profileType() == QaProfileType.FULL_RELEASE;
		boolean hasBlockingFinding = policyEvaluations.stream()
				.anyMatch(pe -> SUBJECT_TYPE_FINDING.equals(pe.getSubjectType()) && PolicyDisposition.BLOCK.name().equals(pe.getDisposition()));
		boolean materiallyUnfulfilledMustBlocks = fullRelease && materiallyUnfulfilledMustRequirementExists;
		boolean authorityEscalate = policyEvaluations.stream()
				.anyMatch(pe -> SUBJECT_TYPE_AUTHORITY_ISSUE.equals(pe.getSubjectType()) && PolicyDisposition.ESCALATE.name().equals(pe.getDisposition()));
		boolean humanReviewEscalate = policyEvaluations.stream().anyMatch(pe ->
				(SUBJECT_TYPE_EVALUATION_ISSUE.equals(pe.getSubjectType()) || SUBJECT_TYPE_FINDING.equals(pe.getSubjectType()))
						&& PolicyDisposition.ESCALATE.name().equals(pe.getDisposition()));

		Set<HoldReason> holdReasons = new LinkedHashSet<>();
		if (evaluationState == QaEvaluationState.INVALID) {
			holdReasons.add(HoldReason.EXECUTION_INVALID);
		} else if (evaluationState == QaEvaluationState.PARTIAL) {
			holdReasons.add(HoldReason.EVALUATION_INCOMPLETE);
		}
		if (!requiredCoverageComplete) {
			holdReasons.add(HoldReason.EVALUATION_INCOMPLETE);
		}
		if (hasBlockingFinding || materiallyUnfulfilledMustBlocks) {
			holdReasons.add(HoldReason.BLOCKING_CANDIDATE_FINDING);
		}
		if (authorityEscalate) {
			holdReasons.add(HoldReason.AUTHORITY_RESOLUTION_REQUIRED);
		}
		if (humanReviewEscalate) {
			holdReasons.add(HoldReason.HUMAN_REVIEW_REQUIRED);
		}

		GateOutcome gateOutcome = holdReasons.isEmpty() && evaluationState == QaEvaluationState.COMPLETE ? GateOutcome.PASS : GateOutcome.HOLD;

		return new QaPolicyAggregationResult(policyEvaluations, gateOutcome, List.copyOf(holdReasons));
	}

	private PolicyDisposition disposeFinding(CandidateFinding finding, QaProfile profile) {
		PolicyDisposition explicit = profile.findingDispositionPolicy().explicitCodeRules().get(finding.getFindingCode());
		if (explicit != null) {
			return explicit;
		}

		if (REQ_MUST_UNFULFILLED_CODE.equals(finding.getFindingCode())) {
			RequirementPolicy requirementPolicy = profile.requirementPolicy().orElse(null);
			if (requirementPolicy != null) {
				return requirementPolicy.materiallyUnfulfilledMust();
			}
		}

		QaSeverity severity = QaSeverity.valueOf(finding.getSeverity());
		PolicyDisposition byDefault = profile.findingDispositionPolicy().severityDefaults().get(severity);
		if (byDefault == null) {
			throw new IllegalStateException(
					"Profile '" + profile.ref() + "' declares no severity-default disposition for severity " + severity);
		}
		return byDefault;
	}

	private String findingPolicyRuleRef(CandidateFinding finding, QaProfile profile) {
		if (profile.findingDispositionPolicy().explicitCodeRules().containsKey(finding.getFindingCode())) {
			return "exact-code:" + finding.getFindingCode();
		}
		if (REQ_MUST_UNFULFILLED_CODE.equals(finding.getFindingCode()) && profile.requirementPolicy().isPresent()) {
			return "requirement-specific:materiallyUnfulfilledMust";
		}
		return "severity-default";
	}
}
