package ai.architech.backend.core.documentation.policy;

/** {@link DocumentationPolicy}'s {@code security} block. */
public record SecurityPolicy(
		boolean forbidSecretValues,
		boolean fieldLevelMinimization,
		boolean preModelSecurityRequired,
		boolean postGenerationSecurityBeforeSemanticModel,
		boolean failClosed,
		boolean audienceClassificationIndependent) {}
