package ai.architech.backend.core.qa.checks;

/**
 * A single check's own result (AIW-172). {@code FAIL} never directly determines the QA gate
 * outcome by itself - that is {@code QAPolicyAggregator}'s job (AIW-176), operating over the
 * {@code CandidateFinding}s a {@code FAIL} may (or may not) give rise to. {@code ERROR} means the
 * check itself could not be reliably completed - it creates Evaluation semantics ({@code
 * EvaluationIssue}), never an automatic Candidate defect.
 */
public enum QaCheckOutcome {
	PASS,
	FAIL,
	ERROR,
	NOT_APPLICABLE
}
