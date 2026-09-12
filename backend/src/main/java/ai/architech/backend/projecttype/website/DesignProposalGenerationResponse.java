package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What the frontend gets back from starting a Designer Agent run - same shape/reasoning as
 * {@code RequirementsAnalysisResponse}: {@code validationIssues} carries only this platform's
 * own validator/reviewer messages, never a raw exception or anything from the AI provider.
 */
public record DesignProposalGenerationResponse(
		UUID executionId,
		String status,
		boolean succeeded,
		List<String> validationIssues,
		String provider,
		String model,
		Integer promptTokens,
		Integer completionTokens,
		BigDecimal costUsd) {

	static DesignProposalGenerationResponse from(DesignProposalGenerationResult result) {
		AgentExecution execution = result.execution();
		return new DesignProposalGenerationResponse(
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
