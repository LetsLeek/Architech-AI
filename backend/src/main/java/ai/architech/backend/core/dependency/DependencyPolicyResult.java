package ai.architech.backend.core.dependency;

import java.util.List;

public record DependencyPolicyResult(List<DependencyPolicyFinding> findings) {

	/** True the moment any single finding is BLOCK - one blocking dependency blocks the whole install. */
	public boolean blocked() {
		return findings.stream().anyMatch(finding -> finding.outcome() == DependencyPolicyOutcome.BLOCK);
	}

	public boolean hasWarnings() {
		return findings.stream().anyMatch(finding -> finding.outcome() == DependencyPolicyOutcome.WARN);
	}
}
