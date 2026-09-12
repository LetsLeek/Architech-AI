package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.artifact.ArtifactVersion;
import java.util.List;

/**
 * What one end-to-end Designer Agent run produced. {@code validationIssues} is a flat,
 * human-readable audit trail across every layer that ran (output-contract, schema, local-ref,
 * structure, canonical-reference, semantic review) - empty exactly when {@link #succeeded()} is
 * true. {@code designProposalSetVersion} is {@code null} unless every required stage passed and
 * the candidate was actually promoted to a canonical {@link ArtifactVersion}.
 */
public record DesignProposalGenerationResult(
		AgentExecution execution, ArtifactVersion designProposalSetVersion, List<String> validationIssues) {

	public boolean succeeded() {
		return designProposalSetVersion != null;
	}
}
