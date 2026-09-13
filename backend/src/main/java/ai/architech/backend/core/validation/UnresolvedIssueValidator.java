package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Detects near-duplicate {@code unresolvedIssues} entries (AIW-142) - two issues reporting the
 * same {@code code} against the same requirement/design/integration-contract refs but with
 * different {@code summary} wording. The schema's own {@code uniqueItems} on {@code
 * unresolvedIssues} already rejects an <em>exactly</em> identical entry, so this validator only
 * needs to catch what schema-level whole-object equality cannot: the same underlying issue
 * restated twice with contradictory or merely differently-worded summaries, which should be one
 * entry, not two.
 */
@Component
public class UnresolvedIssueValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public DeveloperResultValidationResult validate(String resultJson) {
		JsonNode result;
		try {
			result = objectMapper.readTree(resultJson);
		} catch (RuntimeException e) {
			return new DeveloperResultValidationResult(
					false, List.of(new DeveloperResultValidationIssue("unresolved-issue", "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		List<DeveloperResultValidationIssue> issues = new ArrayList<>();
		Set<String> seenKeys = new HashSet<>();
		for (JsonNode issue : result.path("unresolvedIssues")) {
			String key = String.join(
					"|",
					issue.path("code").asString(""),
					sortedRefs(issue.path("relatedRequirementRefs")),
					sortedRefs(issue.path("relatedDesignLocalRefs")),
					sortedRefs(issue.path("relatedIntegrationContractRefs")));
			if (!seenKeys.add(key)) {
				issues.add(new DeveloperResultValidationIssue(
						"unresolved-issue",
						"unresolvedIssues",
						"two unresolved issues report the same code/refs ('" + key + "') with different wording - consolidate into one"));
			}
		}

		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}

	private String sortedRefs(JsonNode refsArray) {
		return StreamSupport.stream(refsArray.spliterator(), false)
				.map(ref -> ref.asString(""))
				.sorted()
				.collect(Collectors.joining(","));
	}
}
