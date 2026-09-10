package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plain unit tests, no Spring context - a real bean list is passed in directly. */
class ModelProfileCredentialsValidatorTests {

	@Test
	void doesNotFailStartupWhenEveryWiredProviderIsConfigured() {
		AiProperties properties = propertiesFor("structured-reasoning", "stub");

		assertThatCode(() -> new ModelProfileCredentialsValidator(properties, List.of(stubProvider("stub", true))))
				.doesNotThrowAnyException();
	}

	@Test
	void doesNotFailStartupWhenAProfileIsWiredToAProviderWithNoMatchingBean() {
		// an unknown provider name is ModelProfileResolver's concern at resolve time, not this
		// validator's at startup - see class javadoc.
		AiProperties properties = propertiesFor("structured-reasoning", "nonexistent");

		assertThatCode(() -> new ModelProfileCredentialsValidator(properties, List.of())).doesNotThrowAnyException();
	}

	@Test
	void failsStartupClearlyWhenAWiredProviderIsMissingRequiredCredentials() {
		AiProperties properties = propertiesFor("structured-reasoning", "stub");

		assertThatThrownBy(() -> new ModelProfileCredentialsValidator(properties, List.of(stubProvider("stub", false))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("structured-reasoning")
				.hasMessageContaining("stub");
	}

	@Test
	void failsStartupClearlyWhenTheFallbackProviderIsMissingRequiredCredentials() {
		AiProperties.ModelProfileConfig config = new AiProperties.ModelProfileConfig(
				"primary", "some-model", new AiProperties.ModelProfileConfig("fallback", "some-model", null));
		AiProperties properties = new AiProperties(Map.of("structured-reasoning", config), Map.of());

		assertThatThrownBy(() -> new ModelProfileCredentialsValidator(
						properties, List.of(stubProvider("primary", true), stubProvider("fallback", false))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("structured-reasoning")
				.hasMessageContaining("fallback");
	}

	private static AiProperties propertiesFor(String profileName, String provider) {
		return new AiProperties(
				Map.of(profileName, new AiProperties.ModelProfileConfig(provider, "some-model", null)), Map.of());
	}

	private static AiProvider stubProvider(String name, boolean configured) {
		return new AiProvider() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public boolean isConfigured() {
				return configured;
			}

			@Override
			public AiResponse invoke(AiRequest request, String model) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
