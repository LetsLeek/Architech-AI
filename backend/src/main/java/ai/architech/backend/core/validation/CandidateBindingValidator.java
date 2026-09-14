package ai.architech.backend.core.validation;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.QaExecution;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Detects the two Candidate-binding problems AIW-169's own acceptance criteria names explicitly:
 * a {@link QaExecution} whose {@code testedCandidateId} does not actually match the Candidate it
 * is being evaluated against, and an observed execution-surface identity that does not match what
 * the execution claims to be bound to (Preview drift). Both are classified as an Evaluation
 * integrity problem - never as a {@link CandidateFinding} against the wrong Candidate ({@code
 * rules/target-input-integrity.md}).
 *
 * <p>{@link #validateExecutionSurface} takes the observed surface identity as a plain parameter
 * rather than fetching it itself - there is no durable Preview-provisioning infrastructure in this
 * codebase yet to observe one from (see {@link QAExecutionPreflightValidator}'s own javadoc for
 * why), so this method is complete and testable now, ready for whatever eventually produces a real
 * observed identity to call it with - the same "build the classifier before the thing it
 * classifies exists" idiom {@code NetworkPolicyChecker} already established for AIW-157.
 */
@Component
public class CandidateBindingValidator {

	public CandidateBindingResult validateCandidate(QaExecution execution, WebsiteImplementationCandidate candidate) {
		if (!execution.getTestedCandidateId().equals(candidate.getId())) {
			return CandidateBindingResult.drift("QaExecution " + execution.getId() + " is bound to Candidate "
					+ execution.getTestedCandidateId() + ", not the Candidate under evaluation (" + candidate.getId() + ")");
		}
		return CandidateBindingResult.bound();
	}

	public CandidateBindingResult validateExecutionSurface(String claimedExecutionSurfaceRef, String observedExecutionSurfaceRef) {
		if (claimedExecutionSurfaceRef == null || observedExecutionSurfaceRef == null) {
			return CandidateBindingResult.bound();
		}
		if (!Objects.equals(claimedExecutionSurfaceRef, observedExecutionSurfaceRef)) {
			return CandidateBindingResult.drift("execution surface drift: claimed '" + claimedExecutionSurfaceRef
					+ "' but observed '" + observedExecutionSurfaceRef + "'");
		}
		return CandidateBindingResult.bound();
	}
}
