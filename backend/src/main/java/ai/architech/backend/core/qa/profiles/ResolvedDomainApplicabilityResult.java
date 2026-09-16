package ai.architech.backend.core.qa.profiles;

import java.util.List;

/** One domain's fully-resolved outcome (AIW-175) - {@link #requiredChecks()} is empty exactly when {@link #outcome()} is {@code NOT_APPLICABLE}. */
public record ResolvedDomainApplicabilityResult(String domain, ResolvedDomainApplicability outcome, List<String> requiredChecks) {}
