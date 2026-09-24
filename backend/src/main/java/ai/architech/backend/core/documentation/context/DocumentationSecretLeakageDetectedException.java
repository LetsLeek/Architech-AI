package ai.architech.backend.core.documentation.context;

/**
 * Thrown by {@link DocumentationSecretScanner} the instant a {@code SecretPatterns.CONTENT_PATTERNS}
 * shape is found anywhere in an about-to-be-frozen {@code DocumentationContext} (AIW-191,
 * {@code DOC-SEC-006} "fail closed and investigate" - genuine leakage blocks canonicalization
 * outright, it is never silently substituted with a {@code [REDACTED:...]} placeholder the way
 * {@code ToolExecutionDiagnostics.sanitize} treats internal diagnostic logs).
 *
 * <p>Per {@code DOC-SEC-007} ("safe diagnostics"), the message names only the matched pattern and a
 * structural location (a {@code resolvedFacts}/{@code authorityCatalog} entry key) - never the
 * matched substring itself.
 */
public class DocumentationSecretLeakageDetectedException extends RuntimeException {

	public DocumentationSecretLeakageDetectedException(String patternName, String location) {
		super("Detected a " + patternName + " shape at " + location + " - blocking context freezing rather than redacting or retrying");
	}
}
