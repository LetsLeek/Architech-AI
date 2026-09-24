package ai.architech.backend.core.qa.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class EvaluationIssueInvariantValidatorTests {

	private final EvaluationIssueInvariantValidator validator = new EvaluationIssueInvariantValidator();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void executionSurfaceDriftWithoutAnExecutionSurfaceRefIsRejected() {
		JsonNode candidate = node("EXECUTION_SURFACE_DRIFT", null, null, null);

		List<FindingInvariantIssue> issues = validator.validate(candidate);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("executionSurfaceRef"));
	}

	@Test
	void executionSurfaceDriftWithAnExecutionSurfaceRefIsValid() {
		JsonNode candidate = node("EXECUTION_SURFACE_DRIFT", "preview-42", null, null);

		assertThat(validator.validate(candidate)).isEmpty();
	}

	@Test
	void requiredCapabilityUnavailableWithNoContextIsRejected() {
		JsonNode candidate = node("REQUIRED_CAPABILITY_UNAVAILABLE", null, null, null);

		List<FindingInvariantIssue> issues = validator.validate(candidate);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("toolCapabilityRef"));
	}

	@Test
	void requiredCapabilityUnavailableWithAToolCapabilityRefIsValid() {
		JsonNode candidate = node("REQUIRED_CAPABILITY_UNAVAILABLE", null, "BROWSER_AUTOMATION", null);

		assertThat(validator.validate(candidate)).isEmpty();
	}

	@Test
	void requiredCapabilityUnavailableWithARequiredCheckRefIsValid() {
		JsonNode candidate = node("REQUIRED_CAPABILITY_UNAVAILABLE", null, null, "CANONICAL_ROUTE_REACHABILITY");

		assertThat(validator.validate(candidate)).isEmpty();
	}

	@Test
	void otherCodesHaveNoAdditionalInvariant() {
		JsonNode candidate = node("TOOL_FAILURE", null, null, null);

		assertThat(validator.validate(candidate)).isEmpty();
	}

	private JsonNode node(String code, String executionSurfaceRef, String toolCapabilityRef, String requiredCheckRef) {
		String json = """
				{"code": "%s", "executionSurfaceRef": %s, "toolCapabilityRef": %s, "requiredCheckRef": %s}
				"""
				.formatted(code, jsonString(executionSurfaceRef), jsonString(toolCapabilityRef), jsonString(requiredCheckRef));
		return objectMapper.readTree(json);
	}

	private String jsonString(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
