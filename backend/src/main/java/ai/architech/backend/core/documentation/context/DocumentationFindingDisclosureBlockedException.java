package ai.architech.backend.core.documentation.context;

/**
 * Thrown by {@link DocumentationFindingDisclosureEvaluator} when a customer-audience evaluation
 * hits a {@code CandidateFinding} whose {@code PolicyEvaluation} disposition is {@code BLOCK}/
 * {@code ESCALATE}, or has no matching {@code PolicyEvaluation} at all - {@code
 * documentation-policy.yaml}'s {@code findingDisclosure.customer.blockingOrUnresolvedEscalation}/
 * {@code notEvaluableMaterial: BLOCK_DOCUMENT}. No {@link DocumentationContext} is ever frozen when
 * this fires - matches {@code validators/candidate/DETERMINISTIC.md}'s "For each {@code
 * BLOCK_DOCUMENT}, no generation should have occurred."
 */
public class DocumentationFindingDisclosureBlockedException extends RuntimeException {

	public DocumentationFindingDisclosureBlockedException(String message) {
		super(message);
	}
}
