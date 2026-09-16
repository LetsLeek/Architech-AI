package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.artifact.CandidateOutput;
import ai.architech.backend.core.qa.QaExecution;
import java.util.List;

/**
 * The outcome of one Website QA semantic Agent call (AIW-173). {@link #candidateOutput()} is
 * always present once the raw model output at least parses as valid JSON under its declared
 * envelope (persisted as permanent audit history regardless of whether it later fails schema or
 * identity validation - the same "never silently lost" reasoning {@code DesignerAgentRunner}
 * already documents), {@code null} only when output-contract parsing itself failed.
 *
 * <p>Deliberately carries no promoted/authoritative artifact of any kind - {@link
 * CandidateOutput} is this result's own final resting place. Converting {@code
 * findingCandidates}/{@code authorityIssueCandidates}/{@code evaluationIssueCandidates} into real,
 * persisted {@code CandidateFinding}/{@code AuthorityIssue}/{@code EvaluationIssue} rows is
 * AIW-174's own scope, not this one's.
 */
public record QaSemanticReviewResult(
		AgentExecution execution, QaExecution qaExecution, CandidateOutput candidateOutput, List<String> issues) {

	public boolean succeeded() {
		return issues.isEmpty();
	}
}
