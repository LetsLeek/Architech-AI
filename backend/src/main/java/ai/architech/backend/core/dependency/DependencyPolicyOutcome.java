package ai.architech.backend.core.dependency;

/** Matches {@code dependency-policy.v1.yaml}'s {@code policyOutcomes} enum exactly. */
public enum DependencyPolicyOutcome {
	PASS,
	WARN,
	BLOCK
}
