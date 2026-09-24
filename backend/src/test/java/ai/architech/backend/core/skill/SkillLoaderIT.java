package ai.architech.backend.core.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SkillLoaderIT {

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
	void resolvesAllTwelveModulesOfTheFrozenPlanWebsiteDesignSkillInOrder() {
		SkillDefinition definition = loader.resolve("plan-website-design", 1);

		assertThat(definition.id()).isEqualTo("plan-website-design");
		assertThat(definition.name()).isEqualTo("Plan Website Design");
		assertThat(definition.modules()).hasSize(12);
		assertThat(definition.modules())
				.extracting(SkillModule::filename)
				.containsExactly(
						"01-input-contract.md",
						"02-design-boundaries.md",
						"03-design-directions.md",
						"04-information-architecture.md",
						"05-pages-navigation.md",
						"06-sections-elements.md",
						"07-design-specification.md",
						"08-responsive-localization.md",
						"09-traceability.md",
						"10-proposal-differentiation.md",
						"11-cross-proposal-consistency.md",
						"12-final-output.md");
	}

	@Test
	void resolvesAllEightModulesOfTheFrozenWebsiteDeveloperSkillInOrder() {
		SkillDefinition definition = loader.resolve("website-developer", 1);

		assertThat(definition.id()).isEqualTo("website-developer");
		assertThat(definition.name()).isEqualTo("Website Developer");
		assertThat(definition.modules()).hasSize(8);
		assertThat(definition.modules())
				.extracting(SkillModule::filename)
				.containsExactly(
						"01-implementation-analysis.md",
						"02-design-to-code.md",
						"03-component-architecture.md",
						"04-responsive-and-styling.md",
						"05-functional-implementation.md",
						"06-dependency-and-project-changes.md",
						"07-verification-and-correction.md",
						"08-traceability-and-handoff.md");
	}

	@Test
	void resolvesAllSixteenFrozenWebsiteQaSkillsEachWithItsOwnOneModule() {
		List<String> qaSkillIds = List.of(
				"website-qa-review",
				"evidence-assessment",
				"finding-construction",
				"requirement-fulfillment-review",
				"customer-fact-review",
				"design-fidelity-review",
				"content-quality-review",
				"functional-behavior-review",
				"navigation-flow-review",
				"responsive-quality-review",
				"visual-defect-review",
				"accessibility-semantic-review",
				"integration-behavior-review",
				"localization-review",
				"finding-deduplication",
				"remediation-reassessment");

		for (String skillId : qaSkillIds) {
			SkillDefinition definition = loader.resolve(skillId, 1);

			assertThat(definition.id()).as("skill id for '%s'", skillId).isEqualTo(skillId);
			assertThat(definition.modules()).as("modules for '%s'", skillId).hasSize(1);
			assertThat(definition.modules().get(0).filename()).isEqualTo("01-" + skillId + ".md");
			assertThat(definition.modules().get(0).content()).isNotBlank();
		}
	}

	@Test
	void resolvesAllNineFrozenDocumentationSkillsEachWithItsOwnOneModule() {
		List<String> documentationSkillIds = List.of(
				"authority-preserving-synthesis",
				"claim-construction-and-traceability",
				"audience-adaptation",
				"epistemic-state-representation",
				"finding-and-limitation-writing",
				"localization-and-terminology",
				"semantic-self-review",
				"customer-handover-composition",
				"technical-handover-composition");

		for (String skillId : documentationSkillIds) {
			SkillDefinition definition = loader.resolve(skillId, 1);

			assertThat(definition.id()).as("skill id for '%s'", skillId).isEqualTo(skillId);
			assertThat(definition.modules()).as("modules for '%s'", skillId).hasSize(1);
			assertThat(definition.modules().get(0).filename()).isEqualTo("01-" + skillId + ".md");
			assertThat(definition.modules().get(0).content()).isNotBlank();
		}
	}

	@Test
	void throwsWhenNoSkillMatchesTheRequestedIdAndVersion() {
		assertThatThrownBy(() -> loader.resolve("extract-business-requirements", 99))
				.isInstanceOf(SkillDefinitionNotFoundException.class);

		assertThatThrownBy(() -> loader.resolve("does-not-exist", 1))
				.isInstanceOf(SkillDefinitionNotFoundException.class);
	}
}
