package ai.architech.backend.projecttype.website;

import java.util.Map;
import java.util.UUID;

/**
 * What {@link InitialGenerationOrchestrator#evaluate} reports for one batch (AIW-146) -
 * {@code candidateIdsByProposalLocalRef} is populated only for {@link
 * InitialGenerationBatchStatus#COMPLETE}, and {@code escalationReason} only for {@link
 * InitialGenerationBatchStatus#ESCALATED}; the other field is empty/{@code null} rather than
 * fabricated, the same "null/not-yet-available states are represented honestly" idiom AIW-160's
 * status API already establishes.
 */
public record InitialGenerationBatchOutcome(
		InitialGenerationBatchStatus status, Map<String, UUID> candidateIdsByProposalLocalRef, String escalationReason) {

	static InitialGenerationBatchOutcome inProgress() {
		return new InitialGenerationBatchOutcome(InitialGenerationBatchStatus.IN_PROGRESS, Map.of(), null);
	}

	static InitialGenerationBatchOutcome complete(Map<String, UUID> candidateIdsByProposalLocalRef) {
		return new InitialGenerationBatchOutcome(
				InitialGenerationBatchStatus.COMPLETE, Map.copyOf(candidateIdsByProposalLocalRef), null);
	}

	static InitialGenerationBatchOutcome escalated(String reason) {
		return new InitialGenerationBatchOutcome(InitialGenerationBatchStatus.ESCALATED, Map.of(), reason);
	}
}
