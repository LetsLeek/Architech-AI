package ai.architech.backend.core.qa.invariants;

import ai.architech.backend.core.validation.EvidenceBindingResult;
import ai.architech.backend.core.validation.EvidenceBindingValidator;
import ai.architech.backend.core.validation.EvidenceReferenceProblem;
import ai.architech.backend.core.validation.RequiredEvidenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Validates one raw {@code remediationAssessmentCandidate} (from AIW-173's own {@code
 * SemanticQAReviewOutput}) against its own {@code proposedStatus}-specific shape requirements
 * (AIW-179), before it is ever eligible to become a real, persisted {@code RemediationAssessment}
 * (AIW-168):
 *
 * <ul>
 *   <li>{@code RESOLVED} requires new-Candidate Evidence - {@code evidenceRefs} must be
 *       non-empty and every ref must pass {@link EvidenceBindingValidator} against the *new*
 *       tested Candidate.
 *   <li>{@code PERSISTS}/{@code CHANGED} must reference one or more current Findings on the new
 *       Candidate - {@code relatedFindingCandidateRefs} must be non-empty and every ref must
 *       resolve to a real {@code localRef} among this same semantic output's own {@code
 *       findingCandidates}.
 *   <li>{@code NOT_EVALUABLE} references an Evaluation Issue rather than guessing - this same
 *       semantic output's own {@code evaluationIssueCandidates} must be non-empty (the frozen
 *       {@code remediationAssessmentCandidate} schema carries no direct evaluation-issue ref
 *       field of its own to point at one more specifically).
 * </ul>
 */
@Component
public class RemediationAssessmentInvariantValidator {

	private final EvidenceBindingValidator evidenceBindingValidator;

	RemediationAssessmentInvariantValidator(EvidenceBindingValidator evidenceBindingValidator) {
		this.evidenceBindingValidator = evidenceBindingValidator;
	}

	public List<FindingInvariantIssue> validate(JsonNode remediationAssessmentCandidate, JsonNode fullSemanticOutput, UUID newTestedCandidateId) {
		String proposedStatus = remediationAssessmentCandidate.path("proposedStatus").asString(null);
		return switch (proposedStatus == null ? "" : proposedStatus) {
			case "RESOLVED" -> validateResolved(remediationAssessmentCandidate, newTestedCandidateId);
			case "PERSISTS", "CHANGED" -> validatePersistsOrChanged(remediationAssessmentCandidate, fullSemanticOutput);
			case "NOT_EVALUABLE" -> validateNotEvaluable(fullSemanticOutput);
			default -> List.of(new FindingInvariantIssue("proposedStatus", "'" + proposedStatus + "' is not a known remediation status"));
		};
	}

	private List<FindingInvariantIssue> validateResolved(JsonNode candidate, UUID newTestedCandidateId) {
		List<String> evidenceRefs = stringArray(candidate.path("evidenceRefs"));
		if (evidenceRefs.isEmpty()) {
			return List.of(new FindingInvariantIssue("evidenceRefs", "RESOLVED requires at least one new-Candidate Evidence reference"));
		}

		EvidenceBindingResult evidenceResult =
				evidenceBindingValidator.validate(newTestedCandidateId, RequiredEvidenceContext.none(), evidenceRefs);
		if (evidenceResult.passed()) {
			return List.of();
		}
		List<FindingInvariantIssue> issues = new ArrayList<>();
		for (EvidenceReferenceProblem problem : evidenceResult.problems()) {
			issues.add(new FindingInvariantIssue("evidenceRefs", problem.problem()));
		}
		return issues;
	}

	private List<FindingInvariantIssue> validatePersistsOrChanged(JsonNode candidate, JsonNode fullSemanticOutput) {
		List<String> relatedRefs = stringArray(candidate.path("relatedFindingCandidateRefs"));
		if (relatedRefs.isEmpty()) {
			return List.of(new FindingInvariantIssue(
					"relatedFindingCandidateRefs", "PERSISTS/CHANGED requires at least one reference to a current Finding on the new Candidate"));
		}

		List<String> knownFindingLocalRefs = new ArrayList<>();
		for (JsonNode findingCandidate : fullSemanticOutput.path("findingCandidates")) {
			knownFindingLocalRefs.add(findingCandidate.path("localRef").asString(null));
		}

		List<FindingInvariantIssue> issues = new ArrayList<>();
		for (String ref : relatedRefs) {
			if (!knownFindingLocalRefs.contains(ref)) {
				issues.add(new FindingInvariantIssue(
						"relatedFindingCandidateRefs", "'" + ref + "' does not resolve to any current Finding candidate in this same output"));
			}
		}
		return issues;
	}

	private List<FindingInvariantIssue> validateNotEvaluable(JsonNode fullSemanticOutput) {
		JsonNode evaluationIssueCandidates = fullSemanticOutput.path("evaluationIssueCandidates");
		if (!evaluationIssueCandidates.isArray() || evaluationIssueCandidates.isEmpty()) {
			return List.of(new FindingInvariantIssue(
					"proposedStatus", "NOT_EVALUABLE must be backed by at least one Evaluation Issue in this same output, never a guess"));
		}
		return List.of();
	}

	private List<String> stringArray(JsonNode arrayNode) {
		List<String> values = new ArrayList<>();
		if (arrayNode.isArray()) {
			arrayNode.forEach(node -> values.add(node.asString()));
		}
		return values;
	}
}
