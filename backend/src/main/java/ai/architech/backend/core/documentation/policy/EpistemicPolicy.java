package ai.architech.backend.core.documentation.policy;

/** {@link DocumentationPolicy}'s {@code epistemic} block. */
public record EpistemicPolicy(
		String unknown, String missingOptionalAuthority, String authorityConflict, String lineageIncompatibility, boolean noInference) {}
