package ai.architech.backend.core.validation;

import ai.architech.backend.core.toolexecution.ToolExecution;
import ai.architech.backend.core.toolexecution.ToolExecutionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cross-checks a {@code BLOCKED} {@code developer-agent-result:v1} candidate's {@code blockers}
 * against available execution evidence (AIW-142's "Blocker claims are checked against available
 * Runtime/Tool/Dependency/Integration execution evidence where authoritative evidence exists").
 *
 * <p>Only {@code TOOL_CAPABILITY_MISSING}/{@code RUNTIME_CAPABILITY_MISSING} are checked here,
 * against {@link ToolExecution} evidence (AIW-148) - a claimed capability blocker with no
 * corresponding {@code DENIED}/{@code ERROR} tool execution anywhere in this execution's own
 * evidence is a "fake blocker" (a Developer-owned failure disguised as a nonlocal one, exactly
 * what this ticket's own rules prohibit). {@code DEPENDENCY_POLICY_BLOCKED} is deliberately not
 * cross-checked: no persisted dependency-policy-evaluation-result exists anywhere in this
 * codebase yet ({@code DependencyPolicyClassifier} is a pure, unpersisted classifier) - there is
 * no authoritative evidence source for that code to check against, so per this ticket's own
 * "where authoritative evidence exists" qualifier, it is correctly left unchecked rather than
 * faked. If {@code toolExecutions} is empty entirely (no evidence exists for this execution at
 * all), every blocker passes untouched for the same reason.
 */
@Component
public class DeveloperBlockerValidator {

	private static final Set<String> CAPABILITY_EVIDENCE_CODES = Set.of("TOOL_CAPABILITY_MISSING", "RUNTIME_CAPABILITY_MISSING");

	private final ObjectMapper objectMapper = new ObjectMapper();

	public DeveloperResultValidationResult validate(String resultJson, List<ToolExecution> toolExecutions) {
		JsonNode result;
		try {
			result = objectMapper.readTree(resultJson);
		} catch (RuntimeException e) {
			return new DeveloperResultValidationResult(
					false, List.of(new DeveloperResultValidationIssue("blocker", "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		if (toolExecutions.isEmpty()) {
			return DeveloperResultValidationResult.passed();
		}

		boolean hasCapabilityFailureEvidence = toolExecutions.stream()
				.anyMatch(execution -> execution.getStatus() == ToolExecutionStatus.DENIED || execution.getStatus() == ToolExecutionStatus.ERROR);

		List<DeveloperResultValidationIssue> issues = new ArrayList<>();
		for (JsonNode blocker : result.path("blockers")) {
			String code = blocker.path("code").asString(null);
			if (CAPABILITY_EVIDENCE_CODES.contains(code) && !hasCapabilityFailureEvidence) {
				issues.add(new DeveloperResultValidationIssue(
						"blocker",
						"blockers.code",
						"blocker claims '" + code + "' but no DENIED/ERROR tool-execution evidence exists for this execution"));
			}
		}

		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}
}
