package ai.architech.backend.core.documentation.policy;

/** The loaded, versioned {@code DOCUMENTATION_POLICY@1.0.0} (AIW-188). */
public record DocumentationPolicy(
		String ref,
		SecurityPolicy security,
		EpistemicPolicy epistemic,
		FindingDisclosurePolicy findingDisclosure,
		ValidationPolicy validation,
		ProfileSpecificOverrides profileSpecificOverrides,
		String note) {}
