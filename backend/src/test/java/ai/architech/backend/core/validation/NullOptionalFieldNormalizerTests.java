package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NullOptionalFieldNormalizerTests {

	private final NullOptionalFieldNormalizer normalizer = new NullOptionalFieldNormalizer(new ObjectMapper());
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void stripsATopLevelNullProperty() {
		String result = normalizer.stripNullFields("""
				{"kind": "hero", "customKind": null}
				""");

		assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree("""
				{"kind": "hero"}
				"""));
	}

	@Test
	void stripsANullPropertyNestedInsideAnObject() {
		String result = normalizer.stripNullFields("""
				{"section": {"kind": "hero", "customKind": null}}
				""");

		assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree("""
				{"section": {"kind": "hero"}}
				"""));
	}

	@Test
	void stripsANullPropertyNestedInsideObjectsWithinAnArray() {
		String result = normalizer.stripNullFields("""
				{"sections": [
				  {"kind": "hero", "customKind": null},
				  {"kind": "custom", "customKind": "banner"}
				]}
				""");

		assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree("""
				{"sections": [
				  {"kind": "hero"},
				  {"kind": "custom", "customKind": "banner"}
				]}
				"""));
	}

	@Test
	void leavesAnEmptyStringValueUntouched() {
		String result = normalizer.stripNullFields("""
				{"customKind": ""}
				""");

		assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree("""
				{"customKind": ""}
				"""));
	}

	@Test
	void leavesAnEmptyObjectAndEmptyArrayUntouched() {
		String result = normalizer.stripNullFields("""
				{"emptyObject": {}, "emptyArray": []}
				""");

		assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree("""
				{"emptyObject": {}, "emptyArray": []}
				"""));
	}

	@Test
	void leavesADeeplyValidCandidateEntirelyUnchangedWhenItHasNoNulls() {
		String json = """
				{"proposals": [{"localRef": "prop-a", "sections": [{"kind": "custom", "customKind": "banner"}]}]}
				""";

		String result = normalizer.stripNullFields(json);

		assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree(json));
	}
}
