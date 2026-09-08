package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WebsiteRequirementsSemanticValidatorTests {

	private final WebsiteRequirementsSemanticValidator validator =
			new WebsiteRequirementsSemanticValidator(new ObjectMapper());

	private static final String VALID_REQUIREMENTS =
			"""
			{
			  "goals": [{"localRef": "goal-1", "description": "Grow leads", "strength": "must", "sourceRefs": ["SRC-1"]}],
			  "targetAudiences": [],
			  "contentRequirements": [{"localRef": "cr-1", "type": "custom", "customType": "recipe-index", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}],
			  "functionalRequirements": [{"localRef": "fr-1", "type": "contact-form", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}],
			  "languages": [{"code": "en", "strength": "must", "sourceRefs": ["SRC-1"]}, {"code": "de-AT", "strength": "should", "sourceRefs": ["SRC-1"]}],
			  "constraints": [],
			  "unknowns": [{"kind": "ambiguous", "field": "x", "description": "unclear", "affects": ["goal-1"], "sourceRefs": ["SRC-1"]}],
			  "conflicts": [{"description": "x", "affects": ["goal-1"], "statements": [{"description": "A", "sourceRefs": ["SRC-1"]}, {"description": "B", "sourceRefs": ["SRC-2"]}]}]
			}
			""";

	@Test
	void acceptsFullyValidRequirements() {
		WebsiteRequirementsValidationResult result = validator.validate(VALID_REQUIREMENTS);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsAnUnknownAffectsReferenceThatDoesNotResolve() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "contentRequirements": [], "functionalRequirements": [],
				  "languages": [], "constraints": [], "conflicts": [],
				  "unknowns": [{"kind": "missing", "description": "x", "affects": ["does-not-exist"]}]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("does not resolve to an existing requirement item"));
	}

	@Test
	void rejectsAConflictAffectsReferenceThatDoesNotResolve() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "contentRequirements": [], "functionalRequirements": [],
				  "languages": [], "constraints": [], "unknowns": [],
				  "conflicts": [{"description": "x", "affects": ["does-not-exist"], "statements": [
				    {"description": "A", "sourceRefs": ["SRC-1"]}, {"description": "B", "sourceRefs": ["SRC-2"]}]}]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("does not resolve to an existing requirement item"));
	}

	@Test
	void rejectsAnInvalidBcp47LanguageTag() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "contentRequirements": [], "functionalRequirements": [],
				  "constraints": [], "unknowns": [], "conflicts": [],
				  "languages": [{"code": "not a tag!!", "strength": "must", "sourceRefs": ["SRC-1"]}]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("BCP 47"));
	}

	@Test
	void rejectsADuplicatedLanguageCode() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "contentRequirements": [], "functionalRequirements": [],
				  "constraints": [], "unknowns": [], "conflicts": [],
				  "languages": [
				    {"code": "en", "strength": "must", "sourceRefs": ["SRC-1"]},
				    {"code": "EN", "strength": "should", "sourceRefs": ["SRC-1"]}
				  ]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("duplicated"));
	}

	@Test
	void rejectsAContentRequirementMissingCustomTypeWhenTypeIsCustom() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "functionalRequirements": [],
				  "languages": [], "constraints": [], "unknowns": [], "conflicts": [],
				  "contentRequirements": [{"localRef": "cr-1", "type": "custom", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("requires 'customType'"));
	}

	@Test
	void rejectsAContentRequirementWithCustomTypeWhenTypeIsNotCustom() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "functionalRequirements": [],
				  "languages": [], "constraints": [], "unknowns": [], "conflicts": [],
				  "contentRequirements": [{"localRef": "cr-1", "type": "gallery", "customType": "x", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("must not have 'customType'"));
	}

	@Test
	void rejectsAFunctionalRequirementMissingCustomTypeWhenTypeIsCustom() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "contentRequirements": [],
				  "languages": [], "constraints": [], "unknowns": [], "conflicts": [],
				  "functionalRequirements": [{"localRef": "fr-1", "type": "custom", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("requires 'customType'"));
	}

	@Test
	void rejectsAnAmbiguousUnknownWithoutSourceRefs() {
		String json =
				"""
				{
				  "goals": [], "targetAudiences": [], "contentRequirements": [], "functionalRequirements": [],
				  "languages": [], "constraints": [], "conflicts": [],
				  "unknowns": [{"kind": "ambiguous", "field": "x", "description": "unclear"}]
				}
				""";

		WebsiteRequirementsValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.reason().contains("ambiguous unknown"));
	}

	@Test
	void reportsUnparseableCandidateAsAFailureRatherThanThrowing() {
		WebsiteRequirementsValidationResult result = validator.validate("not json {{{");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}
}
