package ai.architech.backend.core.qa.invariants;

import ai.architech.backend.core.validation.EvidenceBindingResult;
import ai.architech.backend.core.validation.EvidenceBindingValidator;
import ai.architech.backend.core.validation.EvidenceReferenceProblem;
import ai.architech.backend.core.validation.RequiredEvidenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * {@code core/VALIDATORS.md}'s own {@code FindingInvariantValidator}: "Validates Registry code,
 * primary Domain, normative basis type, Evidence, Candidate attribution and Severity bounds
 * before persistence" (AIW-174). Operates on one raw {@code semanticFindingCandidate} JSON node
 * from a {@code SemanticQAReviewOutput} (AIW-173) - never the persisted, authoritative {@code
 * CandidateFinding} shape itself, since this validator's whole job is deciding whether a
 * candidate is even eligible to become one.
 *
 * <p>"Normative basis refs must resolve inside the active QA Input Snapshot" is interpreted here
 * as the ref string appearing somewhere in the snapshot's own JSON content - the snapshot's exact
 * shape (which authority ref lives at which path) is {@code qa-execution-input.schema.json}'s own
 * concern, not something worth a second, driftable path-by-path re-implementation here.
 *
 * <p>An unresolvable finding code stops further checks for that candidate immediately - domain,
 * severity bounds and allowed normative basis types are all taxonomy-entry-relative and cannot be
 * meaningfully evaluated without one.
 */
@Component
public class FindingInvariantValidator {

	private final EvidenceBindingValidator evidenceBindingValidator;

	FindingInvariantValidator(EvidenceBindingValidator evidenceBindingValidator) {
		this.evidenceBindingValidator = evidenceBindingValidator;
	}

	public List<FindingInvariantIssue> validate(
			JsonNode findingCandidate, FindingTaxonomy taxonomy, UUID testedCandidateId, String qaInputSnapshotJson) {
		List<FindingInvariantIssue> issues = new ArrayList<>();

		String findingCode = findingCandidate.path("findingCode").asString(null);
		String primaryDomain = findingCandidate.path("primaryDomain").asString(null);

		Optional<FindingTaxonomyEntry> resolved = taxonomy.resolve(findingCode);
		if (resolved.isEmpty()) {
			issues.add(new FindingInvariantIssue("findingCode", "finding code '" + findingCode + "' does not exist in the active taxonomy"));
			return issues;
		}
		FindingTaxonomyEntry taxonomyEntry = resolved.get();

		if (!taxonomyEntry.domain().equals(primaryDomain)) {
			issues.add(new FindingInvariantIssue(
					"primaryDomain",
					"finding code '" + findingCode + "' belongs to domain '" + taxonomyEntry.domain() + "', not '" + primaryDomain + "'"));
		}

		validateSeverity(findingCandidate, findingCode, taxonomyEntry, issues);
		validateNormativeBasis(findingCandidate, findingCode, taxonomyEntry, qaInputSnapshotJson, issues);
		validateEvidence(findingCandidate, testedCandidateId, issues);

		return issues;
	}

	private void validateSeverity(
			JsonNode findingCandidate, String findingCode, FindingTaxonomyEntry taxonomyEntry, List<FindingInvariantIssue> issues) {
		String proposedSeverityRaw = findingCandidate.path("proposedSeverity").asString(null);
		QaSeverity proposedSeverity;
		try {
			proposedSeverity = QaSeverity.valueOf(proposedSeverityRaw);
		} catch (RuntimeException e) {
			issues.add(new FindingInvariantIssue("proposedSeverity", "'" + proposedSeverityRaw + "' is not a known severity"));
			return;
		}
		if (!taxonomyEntry.allowsSeverity(proposedSeverity)) {
			issues.add(new FindingInvariantIssue(
					"proposedSeverity",
					"severity '" + proposedSeverity + "' is outside finding code '" + findingCode + "'s own bounds ["
							+ taxonomyEntry.minimumSeverity() + ", " + taxonomyEntry.maximumSeverity() + "]"));
		}
	}

	private void validateNormativeBasis(
			JsonNode findingCandidate,
			String findingCode,
			FindingTaxonomyEntry taxonomyEntry,
			String qaInputSnapshotJson,
			List<FindingInvariantIssue> issues) {
		JsonNode normativeBasisArray = findingCandidate.path("normativeBasis");
		if (!normativeBasisArray.isArray()) {
			return;
		}
		int i = 0;
		for (JsonNode basis : normativeBasisArray) {
			String type = basis.path("type").asString(null);
			String ref = basis.path("ref").asString(null);
			if (!taxonomyEntry.allowedNormativeBasisTypes().contains(type)) {
				issues.add(new FindingInvariantIssue(
						"normativeBasis[" + i + "].type",
						"normative basis type '" + type + "' is not allowed for finding code '" + findingCode + "'"));
			}
			if (ref == null || !qaInputSnapshotJson.contains(ref)) {
				issues.add(new FindingInvariantIssue(
						"normativeBasis[" + i + "].ref", "normative basis ref '" + ref + "' does not resolve inside the active QA Input Snapshot"));
			}
			i++;
		}
	}

	private void validateEvidence(JsonNode findingCandidate, UUID testedCandidateId, List<FindingInvariantIssue> issues) {
		List<String> evidenceRefs = new ArrayList<>();
		JsonNode evidenceRefsArray = findingCandidate.path("evidenceRefs");
		if (evidenceRefsArray.isArray()) {
			evidenceRefsArray.forEach(node -> evidenceRefs.add(node.asString()));
		}
		EvidenceBindingResult evidenceResult = evidenceBindingValidator.validate(testedCandidateId, RequiredEvidenceContext.none(), evidenceRefs);
		if (!evidenceResult.passed()) {
			for (EvidenceReferenceProblem problem : evidenceResult.problems()) {
				issues.add(new FindingInvariantIssue("evidenceRefs", problem.problem()));
			}
		}
	}
}
