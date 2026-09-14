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
	void resolvesTheFrozenDesignIntegrityRule() {
		RuleDefinition definition = loader.resolve("design-integrity", 1);

		assertThat(definition.id()).isEqualTo("design-integrity");
		assertThat(definition.name()).isEqualTo("Design Integrity");
		assertThat(definition.content()).contains("# Design Integrity");
		assertThat(definition.content()).contains("## Canonical Customer Information");
	}

	@Test
	void resolvesTheFrozenWebsiteDeveloperIntegrityRule() {
		RuleDefinition definition = loader.resolve("website-developer-integrity", 1);

		assertThat(definition.id()).isEqualTo("website-developer-integrity");
		assertThat(definition.name()).isEqualTo("Website Developer Integrity");
		assertThat(definition.content()).contains("# Website Developer Integrity Rule V1");
		assertThat(definition.content()).contains("## 1. Authority & Upstream Preservation");
		assertThat(definition.content()).contains("## 8. Untrusted Content & Secrets");
	}

	@Test
	void resolvesTheFrozenWebsiteQaIntegrityRule() {
		RuleDefinition definition = loader.resolve("website-qa-integrity", 1);

		assertThat(definition.id()).isEqualTo("website-qa-integrity");
		assertThat(definition.name()).isEqualTo("Website QA Integrity");
		assertThat(definition.content()).contains("# Website QA Integrity Rule V1");
		assertThat(definition.content()).contains("## 1. Authority");
		assertThat(definition.content()).contains("## 12. Target and Input Integrity");
	}

	@Test
	void throwsWhenNoRuleMatchesTheRequestedIdAndVersion() {
		assertThatThrownBy(() -> loader.resolve("requirements-integrity", 99))
				.isInstanceOf(RuleDefinitionNotFoundException.class);

		assertThatThrownBy(() -> loader.resolve("does-not-exist", 1))
				.isInstanceOf(RuleDefinitionNotFoundException.class);
	}
}
