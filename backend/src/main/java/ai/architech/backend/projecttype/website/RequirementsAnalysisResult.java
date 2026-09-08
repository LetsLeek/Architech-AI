package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.artifact.RequirementsPersistenceResult;
import java.util.List;

/**
 * What one end-to-end Requirements Analysis run produced. {@code validationIssues} is a flat,
 * human-readable audit trail across every deterministic layer that ran (output-contract,
 * schema, local-ref, semantic, cross-artifact) - empty exactly when {@link #succeeded()} is
 * true. The exact issue DTO shape is explicitly not frozen by
 * docs/core/RUNNER_VALIDATION_CONTRACT.md; this is this platform's V1 implementation choice.
 */
public record RequirementsAnalysisResult(
		AgentExecution execution, RequirementsPersistenceResult persistence, List<String> validationIssues) {

	public boolean succeeded() {
		return persistence.persisted();
	}
}
