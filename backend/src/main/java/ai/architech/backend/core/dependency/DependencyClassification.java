package ai.architech.backend.core.dependency;

/** Matches {@code dependency-policy.v1.yaml}'s {@code classification} enum exactly. */
public enum DependencyClassification {
	PLATFORM_APPROVED,
	POLICY_ELIGIBLE,
	PROHIBITED
}
