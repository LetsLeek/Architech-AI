package ai.architech.backend.core.qa.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FindingTaxonomyLoaderIT {

	@Autowired
	private FindingTaxonomyLoader loader;

	@Test
	void loadsTheFrozenTaxonomyWithItsExpectedRef() {
		FindingTaxonomy taxonomy = loader.load();

		assertThat(taxonomy.ref()).isEqualTo("website-qa-finding-taxonomy@1.0.0");
		assertThat(taxonomy.entries()).hasSizeGreaterThanOrEqualTo(50);
	}

	@Test
	void resolvesAKnownFindingCodeWithItsOwnBoundsAndAllowedBasisTypes() {
		FindingTaxonomy taxonomy = loader.load();

		FindingTaxonomyEntry entry = taxonomy.resolve("REQ_MUST_UNFULFILLED").orElseThrow();

		assertThat(entry.domain()).isEqualTo("REQUIREMENT_FULFILLMENT");
		assertThat(entry.minimumSeverity()).isEqualTo(QaSeverity.MAJOR);
		assertThat(entry.maximumSeverity()).isEqualTo(QaSeverity.CRITICAL);
		assertThat(entry.allowedNormativeBasisTypes()).containsExactly("WEBSITE_REQUIREMENT");
	}

	@Test
	void doesNotResolveAnUnknownFindingCode() {
		assertThat(loader.load().resolve("MADE_UP_FINDING_CODE")).isEmpty();
	}
}
