package ai.architech.backend.core.documentation.policy;

import java.util.List;

/** The loaded, versioned {@code DOCUMENTATION_WORKFLOW_POLICY@1.0.0} (AIW-188). */
public record DocumentationWorkflowPolicy(
		String ref,
		List<WorkflowTrigger> enabledTriggers,
		List<WorkflowTrigger> onDemand,
		List<String> optionalRefreshEvents,
		List<String> ignoredAutomaticTriggers,
		boolean triggerIdempotencyRequired,
		boolean productGateDependency,
		int maxGenerationAttempts,
		int maxSemanticEvaluatorExecutionsPerUnchangedCandidate) {}
