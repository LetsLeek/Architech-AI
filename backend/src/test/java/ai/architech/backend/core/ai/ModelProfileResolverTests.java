package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ModelProfileResolverTests {

	@Autowired
	private ModelProfileResolver resolver;

	@Test
	void resolvesTheConfiguredStructuredReasoningProfile() {
		ResolvedModel resolved = resolver.resolve("structured-reasoning");

		assertThat(resolved.provider()).isEqualTo("mock");
		assertThat(resolved.model()).isEqualTo("mock-model");
	}

	@Test
	void throwsForAnUnconfiguredProfile() {
		assertThatThrownBy(() -> resolver.resolve("does-not-exist"))
				.isInstanceOf(UnknownModelProfileException.class);
	}
}
