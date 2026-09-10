package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OutputContractParserIT {

	private static final Set<String> REQUIRED = Set.of("customer-profile", "website-requirements");

	@Autowired
	private OutputContractParser parser;

	@Test
	void acceptsAnEnvelopeWithExactlyTheRequiredArtifacts() {
		String raw = """
				{"customer-profile": {"a": 1}, "website-requirements": {"b": 2}}
				""";

		OutputContractResult result = parser.parse(raw, REQUIRED);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
		assertThat(result.artifactContentByType()).containsOnlyKeys("customer-profile", "website-requirements");
		assertThat(result.artifactContentByType().get("customer-profile")).contains("\"a\"");
	}

	@Test
	void reportsAMissingRequiredArtifact() {
		String raw = """
				{"customer-profile": {"a": 1}}
				""";

		OutputContractResult result = parser.parse(raw, REQUIRED);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.contains("website-requirements"));
	}

	@Test
	void reportsAnUnexpectedTopLevelKey() {
		String raw =
				"""
				{"customer-profile": {}, "website-requirements": {}, "extra-thing": {}}
				""";

		OutputContractResult result = parser.parse(raw, REQUIRED);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.contains("extra-thing"));
	}

	@Test
	void reportsInvalidJsonRatherThanThrowing() {
		OutputContractResult result = parser.parse("not json at all", REQUIRED);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	@Test
	void reportsANonObjectRootAsInvalid() {
		OutputContractResult result = parser.parse("[1, 2, 3]", REQUIRED);

		assertThat(result.valid()).isFalse();
	}
}
