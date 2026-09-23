package ai.architech.backend.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiKeyPropertiesTests {

	@Test
	void acceptsANonBlankKey() {
		ApiKeyProperties properties = new ApiKeyProperties("some-real-key");

		assertThat(properties.apiKey()).isEqualTo("some-real-key");
	}

	@Test
	void rejectsANullKey() {
		assertThatThrownBy(() -> new ApiKeyProperties(null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectsABlankKey() {
		assertThatThrownBy(() -> new ApiKeyProperties("   ")).isInstanceOf(IllegalStateException.class);
	}
}
