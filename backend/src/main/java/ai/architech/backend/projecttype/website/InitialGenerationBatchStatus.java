package ai.architech.backend.projecttype.website;

/**
 * The three states one A/B/C initial-generation batch (AIW-146) can be in. {@code COMPLETE}
 * means all three {@link InitialGenerationSlot}s currently point at a {@code SUCCEEDED}
 * execution with its own accepted Candidate - never merely "three executions finished somehow".
 * {@code ESCALATED} means a sibling exhausted its bounded retry budget, or was semantically
 * {@code BLOCKED}, and mechanical retry stopped rather than looping unboundedly - a human/
 * Workflow decision is required next, which is downstream of this ticket's own scope.
 */
public enum InitialGenerationBatchStatus {
	IN_PROGRESS,
	COMPLETE,
	ESCALATED
}
