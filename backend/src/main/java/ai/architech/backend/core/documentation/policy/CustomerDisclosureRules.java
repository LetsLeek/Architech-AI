package ai.architech.backend.core.documentation.policy;

/** {@link DocumentationPolicy}'s {@code findingDisclosure.customer} block. */
public record CustomerDisclosureRules(
		String qaRequiredProfile,
		String qaRequiredGate,
		String materialCustomerImpact,
		String blockingOrUnresolvedEscalation,
		String resolvedHistorical,
		String notEvaluableMaterial,
		String technicalOnlyNonmaterial,
		String unmappedMaterialFallback) {}
