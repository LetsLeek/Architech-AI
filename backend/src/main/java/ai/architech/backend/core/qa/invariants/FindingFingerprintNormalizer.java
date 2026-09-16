package ai.architech.backend.core.qa.invariants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@code core/VALIDATORS.md}'s own {@code FindingFingerprintNormalizer}: "Produces a stable
 * structured fingerprint from Candidate, finding code, normative basis, route/context/anchors and
 * other stable fields. Do not hash free-form summary alone" (AIW-174). A SHA-256 hex digest over
 * a canonical, sorted, delimited concatenation of exactly those structured fields - the model's
 * own free-form {@code summary} text never enters the fingerprint at all, so two rewordings of
 * the same underlying defect still fingerprint identically.
 */
@Component
public class FindingFingerprintNormalizer {

	private static final String FIELD_SEPARATOR = "";
	private static final String ITEM_SEPARATOR = "";

	public String fingerprint(
			UUID testedCandidateId,
			String findingCode,
			List<String> normativeBasisRefs,
			String route,
			String viewportRef,
			String locale,
			String interactionState,
			List<String> implementationAnchorRefs) {
		String canonical = String.join(
				FIELD_SEPARATOR,
				testedCandidateId.toString(),
				findingCode,
				sortedJoin(normativeBasisRefs),
				nullToEmpty(route),
				nullToEmpty(viewportRef),
				nullToEmpty(locale),
				nullToEmpty(interactionState),
				sortedJoin(implementationAnchorRefs));
		return sha256Hex(canonical);
	}

	private String sortedJoin(List<String> values) {
		return values.stream().sorted().reduce((a, b) -> a + ITEM_SEPARATOR + b).orElse("");
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String sha256Hex(String input) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
		byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}
}
