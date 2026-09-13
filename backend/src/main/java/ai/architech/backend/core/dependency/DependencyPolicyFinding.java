package ai.architech.backend.core.dependency;

/**
 * One dependency-policy decision, auditable on its own - {@code reason} always states exactly
 * why, so a finding is never a bare code the caller has to reverse-engineer. Findings are
 * reported, never repaired: nothing in this package ever rewrites a manifest/lockfile.
 */
public record DependencyPolicyFinding(
		String packageName,
		String versionSpec,
		DependencyClassification classification,
		DependencyPolicyOutcome outcome,
		String reason) {}
