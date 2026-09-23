package ai.architech.backend.core.documentation.policy;

/** {@link DocumentationPolicy}'s {@code findingDisclosure} block. */
public record FindingDisclosurePolicy(
		boolean onlyCurrentCandidateFindings,
		boolean qaGateDoesNotEraseFindings,
		CustomerDisclosureRules customer,
		DeveloperDisclosureRules developer) {}
