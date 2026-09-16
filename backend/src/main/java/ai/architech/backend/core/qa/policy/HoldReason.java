package ai.architech.backend.core.qa.policy;

/** {@code core/POLICY_AGGREGATOR.md}'s own closed, Core-derived set of structured hold reasons (AIW-176). */
public enum HoldReason {
	BLOCKING_CANDIDATE_FINDING,
	AUTHORITY_RESOLUTION_REQUIRED,
	EVALUATION_INCOMPLETE,
	HUMAN_REVIEW_REQUIRED,
	EXECUTION_INVALID
}
