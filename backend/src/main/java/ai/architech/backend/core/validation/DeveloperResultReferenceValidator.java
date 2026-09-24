package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks that every {@code requirementRef}/{@code designLocalRef} a {@code
 * developer-agent-result:v1} candidate cites (across {@code functionalBindings}, {@code
 * unresolvedIssues} and {@code blockers}) actually belongs to the exact
 * website-requirements/target-proposal pair active for this execution - mirrors {@link
 * DesignProposalSetCanonicalReferenceValidator}'s own reasoning for M2. {@code
 * integrationContractRef} authorization is deliberately not checked here - that is {@link
 * IntegrationContractBindingValidator}'s job, since "authorized for this project" needs a live
 * resolver, not a permitted-set membership check against a static document.
 *
 * <p>Detects and reports only: never repairs, renames, or drops an invalid ref from a candidate.
 */
@Component
public class DeveloperResultReferenceValidator {

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
					false, List.of(new DeveloperResultValidationIssue("reference", "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		Set<String> permittedRequirementRefs = new HashSet<>(LocalRefExtractor.extract(websiteRequirements));
		Set<String> permittedDesignLocalRefs = new HashSet<>(LocalRefExtractor.extract(proposal));

		List<DeveloperResultValidationIssue> issues = new ArrayList<>();
		for (JsonNode binding : result.path("functionalBindings")) {
			checkSingularRef(binding, "requirementRef", permittedRequirementRefs, "functionalBindings.requirementRef", issues);
			checkArrayRefs(binding, "designLocalRefs", permittedDesignLocalRefs, "functionalBindings.designLocalRefs", issues);
		}
		for (JsonNode issue : result.path("unresolvedIssues")) {
			checkArrayRefs(issue, "relatedRequirementRefs", permittedRequirementRefs, "unresolvedIssues.relatedRequirementRefs", issues);
			checkArrayRefs(issue, "relatedDesignLocalRefs", permittedDesignLocalRefs, "unresolvedIssues.relatedDesignLocalRefs", issues);
		}
		for (JsonNode blocker : result.path("blockers")) {
			checkArrayRefs(blocker, "relatedRequirementRefs", permittedRequirementRefs, "blockers.relatedRequirementRefs", issues);
			checkArrayRefs(blocker, "relatedDesignLocalRefs", permittedDesignLocalRefs, "blockers.relatedDesignLocalRefs", issues);
		}

		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}

	private void checkSingularRef(
			JsonNode node, String field, Set<String> permitted, String label, List<DeveloperResultValidationIssue> issues) {
		JsonNode ref = node.path(field);
		if (ref.isTextual() && !permitted.contains(ref.asString())) {
			issues.add(new DeveloperResultValidationIssue(
					"reference", label, "'" + ref.asString() + "' is not part of the active website-requirements artifact"));
		}
	}

	private void checkArrayRefs(
			JsonNode node, String field, Set<String> permitted, String label, List<DeveloperResultValidationIssue> issues) {
		for (JsonNode ref : node.path(field)) {
			if (ref.isTextual() && !permitted.contains(ref.asString())) {
				issues.add(new DeveloperResultValidationIssue("reference", label, "'" + ref.asString() + "' is not a known reference"));
			}
		}
	}
}
