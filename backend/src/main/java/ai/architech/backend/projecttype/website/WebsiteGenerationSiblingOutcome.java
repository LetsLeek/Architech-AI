package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;

/**
 * One sibling (A/B/C) proposal's own outcome from {@link WebsiteGenerationDrivingService#generate}
 * - {@code execution}/{@code candidate} are both {@code null} only when the sibling never even
 * reached a real {@link AgentExecution} (an infrastructure failure before/during workspace
 * provisioning or input assembly, captured in {@code infrastructureFailureMessage} instead).
 * {@code candidate} is non-null only when {@code execution} ended {@code SUCCEEDED}, mirroring
 * {@link ai.architech.backend.core.runner.DeveloperToolLoopResult}'s own contract.
 */
public record WebsiteGenerationSiblingOutcome(
		String proposalLocalRef,
		AgentExecution execution,
		WebsiteImplementationCandidate candidate,
		String infrastructureFailureMessage) {}
