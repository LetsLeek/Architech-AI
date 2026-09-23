package ai.architech.backend.core.documentation.policy;

import java.util.List;

/** {@link DocumentationPolicy}'s {@code findingDisclosure.developer} block. */
public record DeveloperDisclosureRules(
		String qaRequiredProfile,
		List<String> qaAllowedGates,
		String allTechnicallyRelevantCurrent,
		String materialSecurityDetail,
		String resolvedHistorical,
		String unmappedCurrentFallback) {}
