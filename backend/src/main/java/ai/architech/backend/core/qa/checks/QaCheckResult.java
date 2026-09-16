package ai.architech.backend.core.qa.checks;

import java.util.List;

/**
 * One executed check's own result (AIW-172) - {@code checkCode} always resolves against the
 * {@link QaCheckRegistry} that produced it. {@link #evidenceRefs()} names the {@code
 * EvidenceRecord} ids ({@code core.qa.EvidenceRecord}, AIW-170) this check's own observation was
 * persisted as - a positive ({@code PASS}) result remains auditable via exactly those refs,
 * satisfying AIW-172's "Positive check execution remains auditable and contributes to Domain
 * coverage."
 */
public record QaCheckResult(String checkCode, QaCheckOutcome outcome, String summary, List<String> evidenceRefs) {

	public static QaCheckResult pass(String checkCode, String summary, List<String> evidenceRefs) {
		return new QaCheckResult(checkCode, QaCheckOutcome.PASS, summary, evidenceRefs);
	}

	public static QaCheckResult fail(String checkCode, String summary, List<String> evidenceRefs) {
		return new QaCheckResult(checkCode, QaCheckOutcome.FAIL, summary, evidenceRefs);
	}

	public static QaCheckResult error(String checkCode, String summary) {
		return new QaCheckResult(checkCode, QaCheckOutcome.ERROR, summary, List.of());
	}

	public static QaCheckResult notApplicable(String checkCode, String summary) {
		return new QaCheckResult(checkCode, QaCheckOutcome.NOT_APPLICABLE, summary, List.of());
	}
}
