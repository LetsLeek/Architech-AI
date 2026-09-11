package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What the frontend gets back from starting a Requirements Analysis run. The run is currently
 * synchronous (the mock AI Gateway call is effectively instant, and no real provider exists
 * yet to make it worth building an async job model for), so by the time this is constructed
 * {@code status} is always a terminal one (SUCCEEDED or FAILED) - never PENDING/RUNNING.
 * {@code validationIssues} deliberately carries only what {@link RequirementsAnalysisRunner}
 * itself produces (deterministic validator messages) - never a raw exception message or
 * anything from the AI provider, so no provider credentials/internals can leak through here.
 *
 * <p>{@code provider}/{@code model}/{@code promptTokens}/{@code completionTokens}/{@code
 * costUsd} (AIW-130) are the same fields {@link AgentExecution} already tracks (AIW-37) -
 * exposing them doesn't weaken the invariant above, since none of the five is a credential or
 * an internal error detail; they're what usage/cost actually was, safe to show a user who ran
 * the analysis themselves. All five are {@code null} together exactly when the execution never
 * reached a real AI call (e.g. failed before invocation) - never fabricated as zero.
 */
public record RequirementsAnalysisResponse(
		UUID executionId,
		String status,
		boolean succeeded,
		List<String> validationIssues,
		String provider,
		String model,
		Integer promptTokens,
		Integer completionTokens,
		BigDecimal costUsd) {

	static RequirementsAnalysisResponse from(RequirementsAnalysisResult result) {
		AgentExecution execution = result.execution();
		return new RequirementsAnalysisResponse(
				execution.getId(),
				execution.getStatus().name(),
				result.succeeded(),
				result.validationIssues(),
				execution.getProvider(),
				execution.getModel(),
				execution.getPromptTokens(),
				execution.getCompletionTokens(),
				execution.getCostUsd());
	}
}
