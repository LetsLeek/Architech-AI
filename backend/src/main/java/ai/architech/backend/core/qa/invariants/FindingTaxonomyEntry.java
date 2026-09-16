package ai.architech.backend.core.qa.invariants;

import java.util.List;

/** One {@code registries/finding-codes.yaml} finding code entry (AIW-174). */
public record FindingTaxonomyEntry(
		String code, String domain, QaSeverity minimumSeverity, QaSeverity maximumSeverity, List<String> allowedNormativeBasisTypes) {

	public boolean allowsSeverity(QaSeverity severity) {
		return severity.compareTo(minimumSeverity) >= 0 && severity.compareTo(maximumSeverity) <= 0;
	}
}
