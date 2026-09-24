package ai.architech.backend.core.qa.invariants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * {@code core/VALIDATORS.md}'s own {@code AuthorityIssueInvariantValidator}: "Enforces
 * code-specific authority invariants. CONFLICTING_AUTHORITY should reference actual conflicting
 * authority; missing-authority codes must identify the expected authority type" (AIW-174).
 * {@code authority-issue.schema.json}'s own closed {@code code} enum ({@code MISSING_AUTHORITY},
 * {@code CONFLICTING_AUTHORITY}, {@code INVALID_AUTHORITY_REFERENCE}, {@code
 * INSUFFICIENT_UPSTREAM_INFORMATION}, {@code MISSING_INTEGRATION_AUTHORITY}) already rejects an
 * unknown code; this validator only adds the two invariants the frozen validator set names by
 * name, above and beyond generic schema validity.
 */
@Component
public class AuthorityIssueInvariantValidator {

	private static final Set<String> MISSING_AUTHORITY_CODES = Set.of("MISSING_AUTHORITY", "MISSING_INTEGRATION_AUTHORITY");
	private static final String CONFLICTING_AUTHORITY_CODE = "CONFLICTING_AUTHORITY";

	public List<FindingInvariantIssue> validate(JsonNode authorityIssueCandidate) {
		List<FindingInvariantIssue> issues = new ArrayList<>();
		String code = authorityIssueCandidate.path("code").asString(null);

		if (MISSING_AUTHORITY_CODES.contains(code) && isEmptyArray(authorityIssueCandidate.path("expectedAuthorityTypes"))) {
			issues.add(new FindingInvariantIssue(
					"expectedAuthorityTypes", "code '" + code + "' must identify at least one expected authority type"));
		}
		if (CONFLICTING_AUTHORITY_CODE.equals(code) && isEmptyArray(authorityIssueCandidate.path("affectedAuthorityRefs"))) {
			issues.add(new FindingInvariantIssue(
					"affectedAuthorityRefs", "code '" + CONFLICTING_AUTHORITY_CODE + "' must reference the actual conflicting authority"));
		}

		return issues;
	}

	private boolean isEmptyArray(JsonNode node) {
		return !node.isArray() || node.isEmpty();
	}
}
