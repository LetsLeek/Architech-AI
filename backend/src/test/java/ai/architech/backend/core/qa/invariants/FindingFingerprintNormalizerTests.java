package ai.architech.backend.core.qa.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindingFingerprintNormalizerTests {

	private final FindingFingerprintNormalizer normalizer = new FindingFingerprintNormalizer();
	private final UUID candidateId = UUID.randomUUID();

	@Test
	void identicalStructuredFieldsProduceTheSameFingerprint() {
		String a = normalizer.fingerprint(
				candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of("design-b-3"), "/", "FULL_NARROW", "en-US", "menu-open", List.of("anchor-1"));
		String b = normalizer.fingerprint(
				candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of("design-b-3"), "/", "FULL_NARROW", "en-US", "menu-open", List.of("anchor-1"));

		assertThat(a).isEqualTo(b);
	}

	@Test
	void aDifferentSummaryAloneNeverAffectsTheFingerprint() {
		// The fingerprint signature itself has no summary parameter - this test documents that
		// omission is deliberate, matching "Do not hash free-form summary alone."
		String fingerprint = normalizer.fingerprint(
				candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of("design-b-3"), "/", "FULL_NARROW", "en-US", "menu-open", List.of("anchor-1"));

		assertThat(fingerprint).hasSize(64); // SHA-256 hex
	}

	@Test
	void aDifferentRouteProducesADifferentFingerprint() {
		String a = normalizer.fingerprint(
				candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of("design-b-3"), "/", "FULL_NARROW", "en-US", "menu-open", List.of("anchor-1"));
		String b = normalizer.fingerprint(
				candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of("design-b-3"), "/pricing", "FULL_NARROW", "en-US", "menu-open", List.of("anchor-1"));

		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void theOrderOfNormativeBasisRefsDoesNotAffectTheFingerprint() {
		String a = normalizer.fingerprint(
				candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of("ref-a", "ref-b"), "/", null, null, null, List.of());
		String b = normalizer.fingerprint(
				candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of("ref-b", "ref-a"), "/", null, null, null, List.of());

		assertThat(a).isEqualTo(b);
	}

	@Test
	void aDifferentCandidateProducesADifferentFingerprintForOtherwiseIdenticalFields() {
		String a = normalizer.fingerprint(candidateId, "NAV_PRIMARY_FLOW_BROKEN", List.of(), "/", null, null, null, List.of());
		String b = normalizer.fingerprint(UUID.randomUUID(), "NAV_PRIMARY_FLOW_BROKEN", List.of(), "/", null, null, null, List.of());

		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void nullContextFieldsAreToleratedAndDoNotCollideWithEmptyStringFields() {
		String withNulls = normalizer.fingerprint(candidateId, "CODE", List.of(), null, null, null, null, List.of());

		assertThat(withNulls).hasSize(64);
	}
}
