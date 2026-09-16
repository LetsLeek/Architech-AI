package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Confirms a {@code semantic-qa-review-output} candidate's own {@code qaExecutionRef}/{@code
 * testedCandidateRef}/{@code qaProfileRef}/{@code inputSnapshotRef} are exactly what this QA
 * execution's own {@code qa-execution-input} claims (AIW-173) - the same "result must echo back
 * the exact identity it was given, never silently accepted as close enough" idiom {@link
 * DeveloperResultTargetIdentityValidator} already establishes. Matching {@code qaProfileRef}
 * exactly is also what makes Comparison and Full Release semantic scope distinguishable: a result
 * claiming the wrong profile is rejected here, never silently treated as the requested scope.
 */
@Component
public class SemanticQaReviewOutputIdentityValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public List<SemanticQaReviewOutputIssue> validate(String resultJson, String qaExecutionInputJson) {
		JsonNode result;
		JsonNode input;
		try {
			result = objectMapper.readTree(resultJson);
			input = objectMapper.readTree(qaExecutionInputJson);
		} catch (RuntimeException e) {
			return List.of(new SemanticQaReviewOutputIssue("$", "candidate is not valid JSON: " + e.getMessage()));
		}

		List<SemanticQaReviewOutputIssue> issues = new ArrayList<>();
		requireMatch(issues, "qaExecutionRef", result.path("qaExecutionRef").asString(null), input.path("qaExecutionRef").asString(null));
		requireMatch(
				issues, "testedCandidateRef", result.path("testedCandidateRef").asString(null),
				input.path("target").path("candidateRef").asString(null));
		requireMatch(
				issues, "qaProfileRef", result.path("qaProfileRef").asString(null),
				input.path("qaAuthority").path("qaProfileRef").asString(null));
		requireMatch(
				issues, "inputSnapshotRef", result.path("inputSnapshotRef").asString(null),
				input.path("provenance").path("inputSnapshotRef").asString(null));
		return issues;
	}

	private void requireMatch(List<SemanticQaReviewOutputIssue> issues, String field, String actual, String expected) {
		if (!Objects.equals(actual, expected)) {
			issues.add(new SemanticQaReviewOutputIssue(
					field, "result claims '" + actual + "' but this execution's own input claims '" + expected + "'"));
		}
	}
}
