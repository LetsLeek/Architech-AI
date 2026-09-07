package ai.architech.backend.core.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SkillLoaderTests {

	@Autowired
	private SkillLoader loader;

	@Test
	void resolvesAllThirteenModulesOfTheFrozenSkillInOrder() {
		SkillDefinition definition = loader.resolve("extract-business-requirements", 1);

		assertThat(definition.id()).isEqualTo("extract-business-requirements");
		assertThat(definition.name()).isEqualTo("Extract Business Requirements");
		assertThat(definition.modules()).hasSize(13);
		assertThat(definition.modules())
				.extracting(SkillModule::filename)
				.containsExactly(
						"01-source-context.md",
						"02-customer-facts.md",
						"03-normalization.md",
						"04-provided-claims.md",
						"05-unknowns-conflicts.md",
						"06-website-intent.md",
						"07-requirement-classification.md",
						"08-no-design-leakage.md",
						"09-strength-classification.md",
						"10-traceability.md",
						"11-cross-artifact-check.md",
						"12-final-consistency.md",
						"13-output.md");
	}

	@Test
	void inlinesActualModuleContentNotJustFileReferences() {
		SkillDefinition definition = loader.resolve("extract-business-requirements", 1);

		assertThat(definition.modules()).allSatisfy(module -> assertThat(module.content()).isNotBlank());

		String inlined = definition.inlinedContent();
		int firstModuleIndex = inlined.indexOf("Use only the immutable Source Context");
		int lastModuleIndex = inlined.indexOf("Emit exactly the two candidate artifacts");
		assertThat(firstModuleIndex).isNotNegative();
		assertThat(lastModuleIndex).isNotNegative();
		assertThat(firstModuleIndex).isLessThan(lastModuleIndex);
	}

	@Test
	void throwsWhenNoSkillMatchesTheRequestedIdAndVersion() {
		assertThatThrownBy(() -> loader.resolve("extract-business-requirements", 99))
				.isInstanceOf(SkillDefinitionNotFoundException.class);

		assertThatThrownBy(() -> loader.resolve("does-not-exist", 1))
				.isInstanceOf(SkillDefinitionNotFoundException.class);
	}
}
