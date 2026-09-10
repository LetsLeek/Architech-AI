package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ModelProfileResolverIT {

	@Autowired
	private ModelProfileResolver resolver;

	@Test
	void resolvesTheConfiguredStructuredReasoningProfile() {
		ResolvedModel resolved = resolver.resolve("structured-reasoning");

		assertThat(resolved.provider()).isEqualTo("mock");
		assertThat(resolved.model()).isEqualTo("mock-model");
		assertThat(resolved.fallback()).isNull();
	}

	@Test
	void throwsForAnUnconfiguredProfile() {
		assertThatThrownBy(() -> resolver.resolve("does-not-exist"))
				.isInstanceOf(UnknownModelProfileException.class);
	}

	@Test
	void resolvesAConfiguredFallbackChain() {
		// plain construction, no Spring context needed - ModelProfileResolver only depends on
		// AiProperties, which is trivially hand-buildable.
		AiProperties.ModelProfileConfig config = new AiProperties.ModelProfileConfig(
				"primary", "primary-model", new AiProperties.ModelProfileConfig("fallback", "fallback-model", null));
		ModelProfileResolver resolverWithFallback =
				new ModelProfileResolver(new AiProperties(Map.of("with-fallback", config), Map.of()));

		ResolvedModel resolved = resolverWithFallback.resolve("with-fallback");

		assertThat(resolved.provider()).isEqualTo("primary");
		assertThat(resolved.model()).isEqualTo("primary-model");
		assertThat(resolved.fallback()).isNotNull();
		assertThat(resolved.fallback().provider()).isEqualTo("fallback");
		assertThat(resolved.fallback().model()).isEqualTo("fallback-model");
	}
}
