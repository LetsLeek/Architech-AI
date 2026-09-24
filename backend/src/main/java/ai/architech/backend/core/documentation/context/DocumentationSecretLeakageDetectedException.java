package ai.architech.backend.core.documentation.context;

/**
 * Thrown by {@link DocumentationSecretScanner} the instant a {@code SecretPatterns.CONTENT_PATTERNS}
 * shape is found anywhere in either scan surface it covers - an about-to-be-frozen {@code
 * DocumentationContext} (AIW-191, pre-generation) or a raw model candidate's own prose (AIW-197,
 * post-generation, {@code DECISION_LOG.md} point 9's "Security ordering") - per {@code DOC-SEC-006}
 * "fail closed and investigate": genuine leakage blocks outright, it is never silently substituted
 * with a {@code [REDACTED:...]} placeholder the way {@code ToolExecutionDiagnostics.sanitize} treats
 * internal diagnostic logs.
 *
 * <p>Per {@code DOC-SEC-007} ("safe diagnostics"), the message names only the matched pattern and a
 * structural location (a {@code resolvedFacts}/{@code authorityCatalog} entry key, or a candidate
 * claim/section path) - never the matched substring itself.
 */
public class DocumentationSecretLeakageDetectedException extends RuntimeException {

	public DocumentationSecretLeakageDetectedException(String patternName, String location) {
		super("Detected a " + patternName + " shape at " + location + " - blocking rather than redacting or retrying");
	}
}
