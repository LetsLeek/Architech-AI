package ai.architech.backend.core.qa.invariants;

import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * {@code core/VALIDATORS.md}'s own {@code FindingDeduplicationValidator}: "Checks structural
 * consistency of semantic deduplication and prevents obvious duplicate persistence/over-merging"
 * (AIW-174). Deliberately the simplest correct rule over {@link FindingFingerprintNormalizer}'s
 * own stable, structured fingerprint: an exact fingerprint match is the same underlying defect
 * (reject as a duplicate); anything else - including two findings that merely share a {@code
 * findingCode} but differ in route/context/anchors - is a genuinely distinct, separately
 * user-impacting defect and is never merged.
 */
@Component
public class FindingDeduplicationValidator {

	public boolean isDuplicate(String candidateFingerprint, Collection<String> existingFingerprints) {
		return existingFingerprints.contains(candidateFingerprint);
	}
}
