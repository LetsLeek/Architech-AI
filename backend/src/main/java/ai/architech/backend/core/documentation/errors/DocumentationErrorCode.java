package ai.architech.backend.core.documentation.errors;

/** One {@code registries/documentation-error-registry.yaml} entry (AIW-188). */
public record DocumentationErrorCode(String code, String issueClass, String defaultRemediationTarget, String retryPolicy, boolean blocking) {}
