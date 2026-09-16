package ai.architech.backend.core.qa.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AuthorityIssueInvariantValidatorTests {

	private final AuthorityIssueInvariantValidator validator = new AuthorityIssueInvariantValidator();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void missingAuthorityRequiresAnExpectedAuthorityType() {
		JsonNode candidate = node("MISSING_AUTHORITY", "[]", "[]");

		List<FindingInvariantIssue> issues = validator.validate(candidate);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("expectedAuthorityTypes"));
	}

	@Test
	void missingIntegrationAuthorityRequiresAnExpectedAuthorityType() {
		JsonNode candidate = node("MISSING_INTEGRATION_AUTHORITY", "[]", "[]");

		List<FindingInvariantIssue> issues = validator.validate(candidate);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("expectedAuthorityTypes"));
	}

	@Test
	void missingAuthorityWithAnExpectedTypeIsValid() {
		JsonNode candidate = node("MISSING_AUTHORITY", "[]", "[\"INTEGRATION_CONTRACT\"]");

		assertThat(validator.validate(candidate)).isEmpty();
	}

	@Test
	void conflictingAuthorityRequiresAnAffectedAuthorityRef() {
		JsonNode candidate = node("CONFLICTING_AUTHORITY", "[]", "[]");

		List<FindingInvariantIssue> issues = validator.validate(candidate);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("affectedAuthorityRefs"));
	}

	@Test
	void conflictingAuthorityWithAnAffectedRefIsValid() {
		JsonNode candidate = node("CONFLICTING_AUTHORITY", "[\"customer-profile-7\"]", "[]");

		assertThat(validator.validate(candidate)).isEmpty();
	}

	@Test
	void otherCodesHaveNoAdditionalInvariant() {
		JsonNode candidate = node("INVALID_AUTHORITY_REFERENCE", "[]", "[]");

		assertThat(validator.validate(candidate)).isEmpty();
	}

	private JsonNode node(String code, String affectedAuthorityRefs, String expectedAuthorityTypes) {
		String json = """
				{"code": "%s", "affectedAuthorityRefs": %s, "expectedAuthorityTypes": %s}
				"""
				.formatted(code, affectedAuthorityRefs, expectedAuthorityTypes);
		return objectMapper.readTree(json);
	}
}
