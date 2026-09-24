package ai.architech.backend.core.validation;

import java.util.List;

/**
 * Whether a set of claimed {@code evidenceRefs} (a {@code CandidateFinding}'s, an {@code
 * AuthorityIssue}'s, or an {@code EvaluationIssue}'s) actually resolves to real, correctly-bound
 * Evidence (AIW-170). {@link #problems()} is empty exactly when {@link #valid()} is {@code true}.
 */
public record EvidenceBindingResult(boolean passed, List<EvidenceReferenceProblem> problems) {

	public static EvidenceBindingResult valid() {
		return new EvidenceBindingResult(true, List.of());
	}

	public static EvidenceBindingResult invalid(List<EvidenceReferenceProblem> problems) {
		return new EvidenceBindingResult(false, List.copyOf(problems));
	}
}
