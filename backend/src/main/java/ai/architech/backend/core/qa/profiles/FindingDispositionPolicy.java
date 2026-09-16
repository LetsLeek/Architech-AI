package ai.architech.backend.core.qa.profiles;

import ai.architech.backend.core.qa.invariants.QaSeverity;
import ai.architech.backend.core.qa.policy.PolicyDisposition;
import java.util.Map;

/**
 * One profile's own {@code findingDispositionPolicy} block (AIW-176) - {@link #explicitCodeRules()}
 * is precedence tier 1 (exact finding-code override), {@link #severityDefaults()} is tier 3 (the
 * fallback when no more specific rule applies). {@code explicitCodeRules} is empty for {@code
 * COMPARISON_READINESS} (the frozen profile declares none).
 */
public record FindingDispositionPolicy(Map<String, PolicyDisposition> explicitCodeRules, Map<QaSeverity, PolicyDisposition> severityDefaults) {}
