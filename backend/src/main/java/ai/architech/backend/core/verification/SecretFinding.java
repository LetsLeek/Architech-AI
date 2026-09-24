package ai.architech.backend.core.verification;

/**
 * One detected secret/credential - deliberately carries no matched value or excerpt, only
 * enough to locate and classify it. "Findings do not echo full secret values into diagnostics"
 * is structural here, not a redaction step applied after the fact: nothing in this package ever
 * stores the matched text anywhere.
 */
public record SecretFinding(String filePath, String patternName, int lineNumber) {}
