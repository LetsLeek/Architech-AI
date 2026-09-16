package ai.architech.backend.core.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceRecordTests {

	@Test
	void recordsAFullyContextedObservation() {
		UUID evidenceManifestId = UUID.randomUUID();
		UUID qaExecutionId = UUID.randomUUID();
		UUID testedCandidateId = UUID.randomUUID();

		EvidenceRecord evidence = new EvidenceRecord(
				evidenceManifestId,
				qaExecutionId,
				testedCandidateId,
				EvidenceRecord.Kind.SCREENSHOT,
				"check:CRITICAL_ELEMENT_VIEWPORT_VISIBILITY",
				"/pricing",
				"viewport-desktop-1440x900",
				"en-US",
				"nav-menu-open",
				"screenshot captured",
				null,
				null);

		assertThat(evidence.getId()).isNotNull();
		assertThat(evidence.getEvidenceManifestId()).isEqualTo(evidenceManifestId);
		assertThat(evidence.getQaExecutionId()).isEqualTo(qaExecutionId);
		assertThat(evidence.getTestedCandidateId()).isEqualTo(testedCandidateId);
		assertThat(evidence.getKind()).isEqualTo(EvidenceRecord.Kind.SCREENSHOT);
		assertThat(evidence.getProducerRef()).isEqualTo("check:CRITICAL_ELEMENT_VIEWPORT_VISIBILITY");
		assertThat(evidence.getRoute()).isEqualTo("/pricing");
		assertThat(evidence.getViewportRef()).isEqualTo("viewport-desktop-1440x900");
		assertThat(evidence.getLocale()).isEqualTo("en-US");
		assertThat(evidence.getInteractionState()).isEqualTo("nav-menu-open");
		assertThat(evidence.getContent()).isEqualTo("screenshot captured");
		assertThat(evidence.getReusedFromEvidenceId()).isNull();
		assertThat(evidence.getReuseJustification()).isNull();
	}

	@Test
	void toleratesAbsentOptionalContextAndReuseFields() {
		EvidenceRecord evidence = new EvidenceRecord(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				EvidenceRecord.Kind.SOURCE_REFERENCE,
				"check:FUNCTIONAL_BINDING_REFERENCE_INTEGRITY",
				null,
				null,
				null,
				null,
				"referenced src/components/Nav.tsx:12",
				null,
				null);

		assertThat(evidence.getRoute()).isNull();
		assertThat(evidence.getViewportRef()).isNull();
		assertThat(evidence.getLocale()).isNull();
		assertThat(evidence.getInteractionState()).isNull();
	}

	@Test
	void recordsExplicitCompatibleReuseFromAnotherEvidenceItem() {
		UUID reusedFrom = UUID.randomUUID();

		EvidenceRecord evidence = new EvidenceRecord(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				EvidenceRecord.Kind.SCANNER_RESULT,
				"check:BROKEN_ASSET_SCAN",
				null,
				null,
				null,
				null,
				"no broken assets found",
				reusedFrom,
				"shared static asset manifest unchanged since the reused Candidate's own Evidence capture");

		assertThat(evidence.getReusedFromEvidenceId()).isEqualTo(reusedFrom);
		assertThat(evidence.getReuseJustification())
				.isEqualTo("shared static asset manifest unchanged since the reused Candidate's own Evidence capture");
	}

	@Test
	void redactsSecretShapedContentBeforeStoringIt() {
		EvidenceRecord evidence = new EvidenceRecord(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				EvidenceRecord.Kind.NETWORK_OBSERVATION,
				"check:INTEGRATION_NETWORK_TARGET_POLICY",
				null,
				null,
				null,
				null,
				"response header x-upstream-key: AKIAABCDEFGHIJKLMNOP",
				null,
				null);

		assertThat(evidence.getContent()).contains("[REDACTED:AWS_ACCESS_KEY]").doesNotContain("AKIAABCDEFGHIJKLMNOP");
	}

	@Test
	void boundsAnOverlongContentValue() {
		String hugeContent = "x".repeat(10_000);

		EvidenceRecord evidence = new EvidenceRecord(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				EvidenceRecord.Kind.DOM_SNAPSHOT,
				"check:EXACT_CUSTOMER_FACT_MATCH",
				null,
				null,
				null,
				null,
				hugeContent,
				null,
				null);

		assertThat(evidence.getContent().length()).isLessThan(hugeContent.length());
		assertThat(evidence.getContent()).endsWith("...[truncated]");
	}
}
