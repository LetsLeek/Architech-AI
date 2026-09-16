package ai.architech.backend.projecttype.website;

import java.util.UUID;

/** One Variant Lineage's current Comparison Readiness eligibility (AIW-177). */
public record VariantEligibility(String variantLineageRef, UUID currentCandidateId, boolean eligible) {}
