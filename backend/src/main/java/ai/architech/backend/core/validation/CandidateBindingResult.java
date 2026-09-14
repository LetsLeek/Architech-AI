package ai.architech.backend.core.validation;

/**
 * Whether one QA observation is correctly bound to the Candidate it claims to be evaluating
 * (AIW-169). A binding problem is never a {@code CandidateFinding} against the intended
 * Candidate - {@code rules/target-input-integrity.md}'s own "execution-surface drift is an
 * Evaluation integrity problem" - which is why this is its own small, dedicated result type
 * rather than folded into {@link PreExecutionValidationIssue}.
 */
public record CandidateBindingResult(boolean matched, String integrityProblem) {

	public static CandidateBindingResult bound() {
		return new CandidateBindingResult(true, null);
	}

	public static CandidateBindingResult drift(String integrityProblem) {
		return new CandidateBindingResult(false, integrityProblem);
	}
}
