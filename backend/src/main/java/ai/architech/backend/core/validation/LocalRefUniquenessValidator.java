package ai.architech.backend.core.validation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * localRef uniqueness (REQUIREMENTS_VALIDATOR_CHECKLIST.md): every localRef within one
 * candidate artifact must be unique - it's how other parts of the same artifact (and, via
 * "affects"/"targetRef", the sibling artifact) point back to a specific item. Generic across
 * both customer-profile and website-requirements: {@link #validate(String)} extracts whatever
 * localRef strings appear in a candidate (locations/offerings/openingHoursSet for
 * customer-profile; goals/audiences/contentRequirements/functionalRequirements/constraints
 * for website-requirements) via {@link LocalRefExtractor}; {@link #validate(List)} is the
 * lower-level entry point for a caller that already has the flat list.
 *
 * <p>{@code objectMapper} is a plain field rather than a constructor dependency so this class
 * keeps its original no-arg-constructible shape (existing plain-JUnit tests rely on {@code new
 * LocalRefUniquenessValidator()}); Spring still autowires it as a component the same way.
 */
@Component
public class LocalRefUniquenessValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public LocalRefValidationResult validate(String candidateJson) {
		JsonNode root;
		try {
			root = objectMapper.readTree(candidateJson);
		} catch (RuntimeException e) {
			return new LocalRefValidationResult(
					false, List.of(new LocalRefValidationIssue("$", "candidate is not valid JSON: " + e.getMessage())));
		}
		return validate(LocalRefExtractor.extract(root));
	}

	public LocalRefValidationResult validate(List<String> localRefs) {
		Map<String, Long> occurrences = localRefs.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		List<LocalRefValidationIssue> issues = occurrences.entrySet().stream()
				.filter(entry -> entry.getValue() > 1)
				.map(entry -> new LocalRefValidationIssue(
						entry.getKey(), "localRef used " + entry.getValue() + " times, must be unique within the artifact"))
				.toList();

		return issues.isEmpty() ? LocalRefValidationResult.passed() : new LocalRefValidationResult(false, issues);
	}
}
