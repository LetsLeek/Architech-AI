package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Computes a {@link ComparisonReadinessBarrier}'s own current readiness (AIW-177) - always fresh,
 * never cached: each slot's eligibility is decided by looking up whether its *current* {@code
 * currentCandidateId} has at least one {@code QaResult} with a matching {@code testedCandidateId}
 * and the exact requested {@code qaProfileRef} whose {@code gateOutcome} is {@code PASS}. Because
 * a remediation cycle always produces a brand-new, distinct Candidate id (Candidates are
 * immutable, AIW-145) rather than mutating the old one, an old {@code QaResult} computed against
 * a since-replaced Candidate can never match the slot's current pointer - "Stale Comparison
 * results cannot qualify newer Candidates" holds by construction, not by an extra staleness check.
 *
 * <p>Never ranks, compares, or picks between slots - each is evaluated strictly independently,
 * matching "QA does not rank or select variants."
 */
@Component
public class ComparisonReadinessBarrierEvaluator {

	private static final String PASS_GATE_OUTCOME = "PASS";

	private final ComparisonReadinessSlotRepository slotRepository;
	private final QaResultRepository qaResultRepository;
	private final WebsiteImplementationCandidateRepository candidateRepository;

	ComparisonReadinessBarrierEvaluator(
			ComparisonReadinessSlotRepository slotRepository,
			QaResultRepository qaResultRepository,
			WebsiteImplementationCandidateRepository candidateRepository) {
		this.slotRepository = slotRepository;
		this.qaResultRepository = qaResultRepository;
		this.candidateRepository = candidateRepository;
	}

	public ComparisonReadinessEvaluation evaluate(UUID barrierId, String qaProfileRef) {
		List<ComparisonReadinessSlot> slots = slotRepository.findByBarrierIdOrderByCreatedAtAsc(barrierId);
		List<VariantEligibility> variants = slots.stream().map(slot -> evaluateSlot(slot, qaProfileRef)).toList();
		boolean allEligible = !variants.isEmpty() && variants.stream().allMatch(VariantEligibility::eligible);
		return new ComparisonReadinessEvaluation(variants, allEligible);
	}

	private VariantEligibility evaluateSlot(ComparisonReadinessSlot slot, String qaProfileRef) {
		WebsiteImplementationCandidate candidate = candidateRepository
				.findById(slot.getCurrentCandidateId())
				.orElseThrow(() -> new IllegalStateException(
						"Comparison Readiness slot " + slot.getId() + " points at a non-existent Candidate " + slot.getCurrentCandidateId()));

		List<QaResult> results = qaResultRepository.findByTestedCandidateIdOrderByCreatedAtAsc(candidate.getId());
		boolean eligible = results.stream()
				.anyMatch(result -> result.getTestedCandidateId().equals(candidate.getId())
						&& result.getQaProfileRef().equals(qaProfileRef)
						&& PASS_GATE_OUTCOME.equals(result.getGateOutcome()));

		return new VariantEligibility(slot.getVariantLineageRef(), candidate.getId(), eligible);
	}
}
