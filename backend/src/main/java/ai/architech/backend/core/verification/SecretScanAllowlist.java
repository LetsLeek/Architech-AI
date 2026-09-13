package ai.architech.backend.core.verification;

import java.util.Set;

/**
 * The fixed, platform-owned set of exact matched values known to be safe placeholders (e.g.
 * widely-documented "this is not a real key" example values) - never a pattern/prefix/substring
 * rule, since a loose allowlist rule is itself a way a real secret could slip through. Hardcoded
 * here rather than read from any repository file, matching the same "Developer cannot disable
 * the scan through repository changes" requirement {@link SecretPatterns} follows.
 */
final class SecretScanAllowlist {

	private static final Set<String> ALLOWED_EXACT_VALUES = Set.of(
			// AWS's own widely-published documentation example access key ID - never a real credential.
			"AKIAIOSFODNN7EXAMPLE");

	static boolean isAllowed(String matchedValue) {
		return ALLOWED_EXACT_VALUES.contains(matchedValue);
	}

	private SecretScanAllowlist() {}
}
