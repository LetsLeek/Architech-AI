package ai.architech.backend.core.validation;

/**
 * The route/viewport/locale/interaction-state context a claim under evaluation actually needs
 * Evidence for ({@code skills/evidence-assessment/SKILL.md}'s "Check ... relevant route/
 * viewport/locale/interaction context") - mirrors {@code CandidateFinding}'s own
 * {@code context} shape (AIW-168's {@code contextJson}). A {@code null} field means that aspect
 * of context is not relevant to the claim being checked, so it is never validated.
 */
public record RequiredEvidenceContext(String route, String viewportRef, String locale, String interactionState) {

	public static RequiredEvidenceContext none() {
		return new RequiredEvidenceContext(null, null, null, null);
	}
}
