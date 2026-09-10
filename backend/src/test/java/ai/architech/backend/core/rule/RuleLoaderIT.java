package ai.architech.backend.core.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RuleLoaderIT {

	@Autowired
	private RuleLoader loader;

	@Test
	void resolvesTheFrozenRequirementsIntegrityRule() {
		RuleDefinition definition = loader.resolve("requirements-integrity", 1);

		assertThat(definition.id()).isEqualTo("requirements-integrity");
		assertThat(definition.name()).isEqualTo("Requirements Integrity");
		assertThat(definition.content()).contains("# Requirements Integrity");
		assertThat(definition.content()).contains("## Evidence");
	}

	@Test
	void throwsWhenNoRuleMatchesTheRequestedIdAndVersion() {
		assertThatThrownBy(() -> loader.resolve("requirements-integrity", 99))
				.isInstanceOf(RuleDefinitionNotFoundException.class);

		assertThatThrownBy(() -> loader.resolve("does-not-exist", 1))
				.isInstanceOf(RuleDefinitionNotFoundException.class);
	}
}
