package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactVersion;
import java.util.Optional;
import java.util.UUID;

/**
 * What the frontend needs to decide when Designer Agent V1 can be invoked and to show the
 * current generation state (AIW-163) - derived entirely from canonical platform state ({@link
 * Artifact}/{@link ArtifactVersion}/{@link AgentExecution}), never from a customer-profile or
 * requirements payload the frontend would otherwise have to send just to ask "am I ready".
 *
 * <p>Deliberately does not carry the design proposal set's own content: {@code
 * designProposalSetArtifactId}/{@code designProposalSetVersionNumber} is only a stable
 * reference, exactly like {@code ArtifactVersionController}'s existing {@code
 * GET /api/projects/{projectId}/artifacts/{type}} already returns - a caller that needs the
 * actual content fetches it from there, so this endpoint never duplicates that retrieval path.
 */
public record DesignerReadinessResponse(
		boolean customerProfileExists,
		boolean websiteRequirementsExists,
		boolean ready,
		boolean running,
		String latestExecutionId,
		String latestExecutionStatus,
		String latestExecutionFailureReason,
		boolean designProposalSetExists,
		UUID designProposalSetArtifactId,
		Integer designProposalSetVersionNumber) {

	static DesignerReadinessResponse from(
			boolean customerProfileExists,
			boolean websiteRequirementsExists,
			Optional<AgentExecution> latestExecution,
			Optional<Artifact> designProposalSetArtifact,
			Optional<ArtifactVersion> latestDesignProposalSetVersion) {
		boolean running = latestExecution
				.map(execution -> execution.getStatus() == AgentExecutionStatus.PENDING
						|| execution.getStatus() == AgentExecutionStatus.RUNNING)
				.orElse(false);

		return new DesignerReadinessResponse(
				customerProfileExists,
				websiteRequirementsExists,
				customerProfileExists && websiteRequirementsExists,
				running,
				latestExecution.map(execution -> execution.getId().toString()).orElse(null),
				latestExecution.map(execution -> execution.getStatus().name()).orElse(null),
				latestExecution.map(AgentExecution::getFailureReason).orElse(null),
				designProposalSetArtifact.isPresent(),
				designProposalSetArtifact.map(Artifact::getId).orElse(null),
				latestDesignProposalSetVersion.map(ArtifactVersion::getVersionNumber).orElse(null));
	}
}
