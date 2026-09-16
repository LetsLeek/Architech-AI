package ai.architech.backend.projecttype.website;

import java.util.List;

/**
 * A {@link ComparisonReadinessBarrier}'s own freshly-computed readiness (AIW-177) - "Customer
 * comparison exposure requires valid PASS for all three required Variant Lineages."
 */
public record ComparisonReadinessEvaluation(List<VariantEligibility> variants, boolean allEligible) {}
