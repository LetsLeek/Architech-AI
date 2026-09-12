package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks that every {@code requirementRef}/{@code customerDataRef} a {@code
 * design-proposal-set} candidate cites actually belongs to the two canonical artifacts active
 * for that execution (AIW-122) - not merely "some localRef that exists somewhere", which would
 * let a candidate cite a fragment from an unrelated project/execution, or cite a
 * customer-profile fragment as if it were a requirement (or vice versa).
 *
 * <p>A "permitted" reference is simply a {@code localRef} already present in the corresponding
 * canonical artifact's own JSON - the same value a requirements-agent candidate assigned itself
 * (via {@link LocalRefUniquenessValidator}) becomes the "canonical reference" a later agent may
 * cite, once that artifact has been persisted. {@code requirementRefs}/{@code customerDataRefs}
 * are validated against two <em>separate</em> permitted sets (one per artifact), which is what
 * makes "resolve only to permitted website-requirements fragments" (not customer-profile ones,
 * and vice versa) true by construction rather than a separate check: a value that only exists in
 * the other artifact simply isn't in the set being checked against.
 *
 * <p>This closes the deterministic floor of "customer-data and requirement navigation targets
 * refer to ... canonical destinations where deterministically knowable": {@link
 * RequirementRefExtractor}/{@link CustomerDataRefExtractor} already walk a {@code
 * navigationTarget}'s singular {@code requirementRef}/{@code customerDataRef} fields exactly
 * like every other occurrence, so a dangling navigation target is reported the same way a
 * dangling reference anywhere else is. Whether a resolvable target is <em>semantically</em>
 * actionable (a genuinely useful thing to navigate to) is a semantic-review question - AIW-123's
 * scope, not this deterministic one's.
 *
 * <p>Detects and reports only: never repairs, renames, or drops an invalid ref from a candidate.
 */
@Component
public class DesignProposalSetCanonicalReferenceValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public DesignProposalSetValidationResult validate(
			String designProposalSetJson, String customerProfileJson, String websiteRequirementsJson) {
		JsonNode root;
		JsonNode customerProfileRoot;
		JsonNode websiteRequirementsRoot;
		try {
			root = objectMapper.readTree(designProposalSetJson);
			customerProfileRoot = objectMapper.readTree(customerProfileJson);
			websiteRequirementsRoot = objectMapper.readTree(websiteRequirementsJson);
		} catch (RuntimeException e) {
			return new DesignProposalSetValidationResult(
					false, List.of(new DesignProposalSetValidationIssue(null, "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		Set<String> permittedCustomerDataRefs = new HashSet<>(LocalRefExtractor.extract(customerProfileRoot));
		Set<String> permittedRequirementRefs = new HashSet<>(LocalRefExtractor.extract(websiteRequirementsRoot));

		List<DesignProposalSetValidationIssue> issues = new ArrayList<>();
		for (JsonNode proposal : root.path("proposals")) {
			String proposalRef = proposal.path("localRef").asString(null);

			for (String requirementRef : RequirementRefExtractor.extract(proposal)) {
				if (!permittedRequirementRefs.contains(requirementRef)) {
					issues.add(new DesignProposalSetValidationIssue(
							proposalRef, "requirementRef", "'" + requirementRef + "' is not part of the active website-requirements artifact"));
				}
			}
			for (String customerDataRef : CustomerDataRefExtractor.extract(proposal)) {
				if (!permittedCustomerDataRefs.contains(customerDataRef)) {
					issues.add(new DesignProposalSetValidationIssue(
							proposalRef, "customerDataRef", "'" + customerDataRef + "' is not part of the active customer-profile artifact"));
				}
			}
		}

		return issues.isEmpty() ? DesignProposalSetValidationResult.passed() : new DesignProposalSetValidationResult(false, issues);
	}
}
