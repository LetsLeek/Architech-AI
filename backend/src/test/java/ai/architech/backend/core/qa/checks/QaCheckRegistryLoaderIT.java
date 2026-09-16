package ai.architech.backend.core.qa.checks;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.qa.tooling.QaToolCapability;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real-classpath proof of AIW-172's own "Registry check codes are loaded/versioned and unknown check codes are rejected." */
@SpringBootTest
class QaCheckRegistryLoaderIT {

	@Autowired
	private QaCheckRegistryLoader loader;

	@Test
	void loadsTheFrozenRegistryWithItsExpectedRef() {
		QaCheckRegistry registry = loader.load();

		assertThat(registry.ref()).isEqualTo("website-qa-check-registry@1.0.0");
		assertThat(registry.checks()).hasSizeGreaterThanOrEqualTo(35);
	}

	@Test
	void resolvesAKnownDeterministicCheckWithItsBoundCapability() {
		QaCheckRegistry registry = loader.load();

		QaCheckRegistryEntry entry = registry.resolve("CANONICAL_ROUTE_REACHABILITY").orElseThrow();

		assertThat(entry.domain()).isEqualTo("RUNTIME_BROWSER");
		assertThat(entry.category()).isEqualTo(QaCheckCategory.DETERMINISTIC);
		assertThat(entry.capability()).contains(QaToolCapability.BROWSER_AUTOMATION);
	}

	@Test
	void resolvesAPlatformCoreCheckWithNoBoundCapability() {
		QaCheckRegistry registry = loader.load();

		QaCheckRegistryEntry entry = registry.resolve("CANDIDATE_VERIFICATION_PROVENANCE").orElseThrow();

		assertThat(entry.category()).isEqualTo(QaCheckCategory.PRECONDITION);
		assertThat(entry.capability()).isEqualTo(Optional.empty());
	}

	@Test
	void doesNotResolveAnUnknownCheckCode() {
		QaCheckRegistry registry = loader.load();

		assertThat(registry.resolve("MADE_UP_CHECK_CODE")).isEmpty();
	}
}
