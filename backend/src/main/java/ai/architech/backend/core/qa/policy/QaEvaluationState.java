package ai.architech.backend.core.qa.policy;

/** {@code QaResult.evaluationState}'s own closed set (AIW-176) - only {@link #COMPLETE} can ever produce a {@code PASS} gate outcome. */
public enum QaEvaluationState {
	COMPLETE,
	PARTIAL,
	INVALID
}
