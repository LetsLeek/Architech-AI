package ai.architech.backend.core.documentation.policy;

/** {@link DocumentationPolicy}'s {@code profileSpecificOverrides} block. */
public record ProfileSpecificOverrides(boolean allowWeakeningGlobalAuthority, boolean allowSecrets, boolean allowBypassSemanticValidation) {}
