package ai.architech.backend.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTests {

	@Test
	void nullAllowedOriginsBecomesAnEmptyListRatherThanNpeingLater() {
		CorsProperties properties = new CorsProperties(null);

		assertThat(properties.allowedOrigins()).isEmpty();
	}

	@Test
	void realOriginsArePreservedAsGiven() {
		CorsProperties properties = new CorsProperties(List.of("https://lively-tree-0a6c93e10.5.azurestaticapps.net"));

		assertThat(properties.allowedOrigins()).containsExactly("https://lively-tree-0a6c93e10.5.azurestaticapps.net");
	}
}
