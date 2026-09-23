package ai.architech.backend.core.documentation.policy;

/** {@link DocumentationPolicy}'s {@code validation} block. */
public record ValidationPolicy(
		boolean semanticFactualConsistencyRequired,
		boolean unsupportedClaimBlocks,
		boolean notEvaluableBlocks,
		boolean requiredDisclosureSemanticFidelity,
		boolean includeBoundedCounterfacts) {}
