package ai.architech.backend.core.validation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * localRef uniqueness (REQUIREMENTS_VALIDATOR_CHECKLIST.md): every localRef within one
 * candidate artifact must be unique - it's how other parts of the same artifact (and, via
 * "affects"/"targetRef", the sibling artifact) point back to a specific item. Generic across
 * both customer-profile and website-requirements: the caller extracts whatever localRef
 * strings appear in a candidate (locations/offerings/openingHoursSet for customer-profile;
 * goals/audiences/contentRequirements/functionalRequirements/constraints for
 * website-requirements) - this validator only ever sees a flat list of strings.
 */
@Component
public class LocalRefUniquenessValidator {

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
