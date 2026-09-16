package ai.architech.backend.core.qa.invariants;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * {@code core/VALIDATORS.md}'s own {@code EvaluationIssueInvariantValidator}: "Enforces
 * code-specific evaluation context, e.g. drift requires an execution surface; missing capability
 * requires capability/check context" (AIW-174). {@code evaluation-issue.schema.json}'s own closed
 * {@code code} enum already rejects an unknown code; this validator only adds the two
 * code-specific context invariants the frozen validator set names by name.
 */
@Component
public class EvaluationIssueInvariantValidator {

	private static final String EXECUTION_SURFACE_DRIFT_CODE = "EXECUTION_SURFACE_DRIFT";
	private static final String REQUIRED_CAPABILITY_UNAVAILABLE_CODE = "REQUIRED_CAPABILITY_UNAVAILABLE";

	public List<FindingInvariantIssue> validate(JsonNode evaluationIssueCandidate) {
		List<FindingInvariantIssue> issues = new ArrayList<>();
		String code = evaluationIssueCandidate.path("code").asString(null);

		if (EXECUTION_SURFACE_DRIFT_CODE.equals(code) && isBlank(evaluationIssueCandidate.path("executionSurfaceRef"))) {
			issues.add(new FindingInvariantIssue(
					"executionSurfaceRef", "code '" + EXECUTION_SURFACE_DRIFT_CODE + "' requires an execution surface reference"));
		}
		if (REQUIRED_CAPABILITY_UNAVAILABLE_CODE.equals(code)
				&& isBlank(evaluationIssueCandidate.path("toolCapabilityRef"))
				&& isBlank(evaluationIssueCandidate.path("requiredCheckRef"))) {
			issues.add(new FindingInvariantIssue(
					"toolCapabilityRef",
					"code '" + REQUIRED_CAPABILITY_UNAVAILABLE_CODE + "' requires a tool capability or required check reference"));
		}

		return issues;
	}

	private boolean isBlank(JsonNode node) {
		String value = node.asString(null);
		return value == null || value.isBlank();
	}
}
