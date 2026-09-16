package ai.architech.backend.core.qa.invariants;

import java.util.List;
import java.util.Optional;

/** The loaded, versioned {@code website-qa-finding-taxonomy@1.0.0} (AIW-174). */
public record FindingTaxonomy(String ref, List<FindingTaxonomyEntry> entries) {

	public Optional<FindingTaxonomyEntry> resolve(String code) {
		return entries.stream().filter(e -> e.code().equals(code)).findFirst();
	}
}
