package ai.architech.backend.core.documentation.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LocaleRegistryLoaderIT {

	@Autowired
	private LocaleRegistryLoader loader;

	@Test
	void loadsTheFrozenLocaleRegistryWithBothActiveLocales() {
		LocaleRegistry registry = loader.load();

		assertThat(registry.registryVersion()).isEqualTo("1.0.0");
		assertThat(registry.activeLocales()).containsExactlyInAnyOrder("de-AT", "en-GB");
	}
}
