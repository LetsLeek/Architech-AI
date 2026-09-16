package ai.architech.backend.core.qa.checks;

import java.util.List;
import java.util.UUID;

/**
 * Everything any single check in {@link QaCheckRunner} might need (AIW-172) - deliberately one
 * generic bag rather than one bespoke parameter type per check, since no higher-level orchestrator
 * that would assemble a minimal per-check context yet exists (that is {@code QAPolicyAggregator}/
 * the semantic Agent integration's own future scope, AIW-173/176). Fields irrelevant to a given
 * check are simply left unused by it.
 */
public record QaCheckExecutionContext(
		UUID projectId,
		String qaExecutionInputJson,
		String claimedExecutionSurfaceRef,
		String observedExecutionSurfaceRef,
		String baseUrl,
		String route,
		int viewportWidth,
		int viewportHeight,
		String requiredText,
		String interactionSelector,
		List<String> localeRoutePrefixes) {}
