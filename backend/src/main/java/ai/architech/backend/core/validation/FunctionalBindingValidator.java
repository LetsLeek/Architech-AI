package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks {@code functionalBindings} coverage (AIW-142): the "proposal-scoped functional
 * coverage" set is every {@code website-requirements} {@code functionalRequirement} the target
 * proposal actually addresses (its {@code localRef} appears anywhere the proposal cites a
 * {@code requirementRef}, via {@link RequirementRefExtractor}'s generic walk) - every requirement
 * in that set must have exactly one binding, and every binding's {@code requirementRef} must be
 * in that set (a binding for a requirement the proposal never addressed, or for a non-functional
 * requirement, is rejected the same way a foreign reference is).
 */
@Component
public class FunctionalBindingValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public DeveloperResultValidationResult validate(String resultJson, String websiteRequirementsJson, String proposalJson) {
		JsonNode result;
		JsonNode websiteRequirements;
		JsonNode proposal;
		try {
			result = objectMapper.readTree(resultJson);
			websiteRequirements = objectMapper.readTree(websiteRequirementsJson);
			proposal = objectMapper.readTree(proposalJson);
		} catch (RuntimeException e) {
			return new DeveloperResultValidationResult(
					false,
					List.of(new DeveloperResultValidationIssue("functional-binding", "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		Set<String> functionalRequirementRefs = new HashSet<>();
		for (JsonNode functionalRequirement : websiteRequirements.path("functionalRequirements")) {
			String ref = functionalRequirement.path("localRef").asString(null);
			if (ref != null) {
				functionalRequirementRefs.add(ref);
			}
		}
		Set<String> addressedByProposal = new HashSet<>(RequirementRefExtractor.extract(proposal));
		addressedByProposal.retainAll(functionalRequirementRefs);

		List<DeveloperResultValidationIssue> issues = new ArrayList<>();
		Map<String, Integer> bindingCountByRequirement = new HashMap<>();
		for (JsonNode binding : result.path("functionalBindings")) {
			String requirementRef = binding.path("requirementRef").asString(null);
			bindingCountByRequirement.merge(requirementRef, 1, Integer::sum);
			if (!addressedByProposal.contains(requirementRef)) {
				issues.add(new DeveloperResultValidationIssue(
						"functional-binding",
						"functionalBindings.requirementRef",
						"'" + requirementRef + "' is not a functional requirement this proposal addresses"));
			}
		}
		bindingCountByRequirement.forEach((requirementRef, count) -> {
			if (count > 1) {
				issues.add(new DeveloperResultValidationIssue(
						"functional-binding",
						"functionalBindings.requirementRef",
						"'" + requirementRef + "' has " + count + " bindings - exactly one is required"));
			}
		});
		for (String requirementRef : addressedByProposal) {
			if (!bindingCountByRequirement.containsKey(requirementRef)) {
				issues.add(new DeveloperResultValidationIssue(
						"functional-binding",
						"functionalBindings",
						"functional requirement '" + requirementRef + "' addressed by the proposal has no binding"));
			}
		}

		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}
}
