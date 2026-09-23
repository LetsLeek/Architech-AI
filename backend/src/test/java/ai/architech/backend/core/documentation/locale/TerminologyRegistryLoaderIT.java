package ai.architech.backend.core.documentation.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TerminologyRegistryLoaderIT {

	@Autowired
	private TerminologyRegistryLoader loader;

	@Test
	void resolvesDeAtWithItsOwnLocalizedSectionTitles() {
		TerminologyRegistry registry = loader.resolve("de-AT");

		assertThat(registry.locale()).isEqualTo("de-AT");
		assertThat(registry.sectionTitles()).containsEntry("WEBSITE_OVERVIEW", "Website-Überblick");
		assertThat(registry.sectionTitles()).containsEntry("KNOWN_LIMITATIONS", "Bekannte Einschränkungen");
		assertThat(registry.protectedEnums()).contains("FULL_RELEASE", "PASS", "HOLD");
	}

	@Test
	void resolvesEnGbWithItsOwnLocalizedSectionTitles() {
		TerminologyRegistry registry = loader.resolve("en-GB");

		assertThat(registry.locale()).isEqualTo("en-GB");
		assertThat(registry.protectedEnums()).contains("FULL_RELEASE", "PASS", "HOLD");
		assertThat(registry.sectionTitles()).containsKey("WEBSITE_OVERVIEW");
	}

	@Test
	void rejectsAnUnknownLocale() {
		assertThatThrownBy(() -> loader.resolve("fr-FR")).isInstanceOf(TerminologyRegistryNotFoundException.class);
	}
}
