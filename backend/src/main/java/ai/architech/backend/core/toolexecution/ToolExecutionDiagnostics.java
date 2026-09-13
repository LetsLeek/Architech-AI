package ai.architech.backend.core.toolexecution;

import ai.architech.backend.core.verification.SecretPatterns;
import java.util.regex.Matcher;

/**
 * Sanitizes raw tool output (stdout/stderr, process error messages) before it is persisted as a
 * {@link ToolExecution}'s diagnostic summary. Reuses {@link SecretPatterns#CONTENT_PATTERNS} -
 * the same shapes {@code SecretScanGate} looks for in committed source (AIW-158) - rather than
 * a second, drifting definition of what a secret looks like, then bounds the result so one
 * pathological tool output can never grow this table unbounded.
 */
final class ToolExecutionDiagnostics {

	private static final int MAX_LENGTH = 4000;
	private static final String TRUNCATION_SUFFIX = "...[truncated]";

	private ToolExecutionDiagnostics() {}

	static String sanitize(String rawDiagnostics) {
		if (rawDiagnostics == null) {
			return null;
		}
		String redacted = rawDiagnostics;
		for (SecretPatterns.NamedPattern namedPattern : SecretPatterns.CONTENT_PATTERNS) {
			Matcher matcher = namedPattern.pattern().matcher(redacted);
			redacted = matcher.replaceAll(Matcher.quoteReplacement("[REDACTED:" + namedPattern.name() + "]"));
		}
		if (redacted.length() > MAX_LENGTH) {
			return redacted.substring(0, MAX_LENGTH) + TRUNCATION_SUFFIX;
		}
		return redacted;
	}
}
