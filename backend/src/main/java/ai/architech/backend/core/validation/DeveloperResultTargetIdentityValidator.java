package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Confirms a {@code developer-agent-result:v1} candidate's own {@code targetDesign} identity is
 * exactly the Design this execution was actually given (AIW-142's "exact target Design identity
 * validation against the AgentExecution input") - a result that names a different design
 * artifact version, or a different proposal within the same artifact, than what
 * {@code developer-execution-input.v1} embedded is rejected outright rather than silently
 * accepted as "close enough".
 */
@Component
public class DeveloperResultTargetIdentityValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public DeveloperResultValidationResult validate(String resultJson, String executionInputJson) {
		JsonNode result;
		JsonNode input;
		try {
			result = objectMapper.readTree(resultJson);
			input = objectMapper.readTree(executionInputJson);
		} catch (RuntimeException e) {
			return new DeveloperResultValidationResult(
					false, List.of(new DeveloperResultValidationIssue("target-identity", "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		String resultDesignRef = result.path("targetDesign").path("designArtifactVersionRef").asString(null);
		String resultProposalRef = result.path("targetDesign").path("proposalLocalRef").asString(null);
		String expectedDesignRef = input.path("targetDesign").path("designArtifactVersionRef").asString(null);
		String expectedProposalRef = input.path("targetDesign").path("targetProposalLocalRef").asString(null);

		List<DeveloperResultValidationIssue> issues = new ArrayList<>();
		if (!Objects.equals(resultDesignRef, expectedDesignRef)) {
			issues.add(new DeveloperResultValidationIssue(
					"target-identity",
					"targetDesign.designArtifactVersionRef",
					"result targets design artifact version '" + resultDesignRef + "' but this execution's input is '" + expectedDesignRef + "'"));
		}
		if (!Objects.equals(resultProposalRef, expectedProposalRef)) {
			issues.add(new DeveloperResultValidationIssue(
					"target-identity",
					"targetDesign.proposalLocalRef",
					"result targets proposal '" + resultProposalRef + "' but this execution's input targets '" + expectedProposalRef + "'"));
		}
		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}
}
