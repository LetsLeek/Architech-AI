package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.qa.AuthorityIssue;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.EvaluationIssue;
import ai.architech.backend.core.qa.policy.QaEvaluationState;
import java.util.List;

/**
 * One {@code semantic-qa-review-output} candidate parsed into real, unsaved child rows plus the
 * deterministic-aggregation input signals {@link SemanticQaReviewOutputToFindingsConverter}
 * derives directly from the candidate's own content (AIW-208) - see that class's own javadoc for
 * exactly how each field is computed and the honest limitations of doing so without a real
 * Requirement-coverage/Domain-applicability validator wired in yet.
 */
public record ConvertedQaCandidate(
		List<CandidateFinding> findings,
		List<AuthorityIssue> authorityIssues,
		List<EvaluationIssue> evaluationIssues,
		String domainResultsJson,
		boolean requiredCoverageComplete,
		boolean materiallyUnfulfilledMustRequirementExists,
		QaEvaluationState evaluationState) {}
