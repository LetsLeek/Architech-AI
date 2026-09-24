package ai.architech.backend.core.qa.profiles;

import ai.architech.backend.core.qa.policy.PolicyDisposition;

/**
 * One profile's own {@code requirementPolicy} block (AIW-176) - only {@code
 * website-qa-full-release@1.0.0} declares one; {@code COMPARISON_READINESS} has none. {@link
 * #materiallyUnfulfilledMust()} is precedence tier 2 (requirement-specific rule) for the {@code
 * REQ_MUST_UNFULFILLED} finding code specifically - the one finding code whose own purpose is
 * exactly "a must Requirement was materially unfulfilled."
 */
public record RequirementPolicy(PolicyDisposition materiallyUnfulfilledMust, boolean absentCouldWithoutDefectiveImplementationCreateFinding) {}
