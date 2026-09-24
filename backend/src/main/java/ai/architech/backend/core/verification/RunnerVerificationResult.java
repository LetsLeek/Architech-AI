package ai.architech.backend.core.verification;

import java.util.List;

/**
 * The complete result of one {@code AuthoritativeRunnerVerifier} run: every {@link GateResult}
 * that actually executed (a fail-fast run stops at the first non-PASS gate, so this list may be
 * shorter than the full mandatory-gate set - the gates that never ran simply have no evidence to
 * report, per AIW-143's own "structured auditable evidence" for gates that ran). {@link
 * #outcome()} is {@code ERROR} if any gate errored, else {@code FAIL} if any gate failed, else
 * {@code PASS} - computed from the evidence itself, not assumed from run order.
 */
public record RunnerVerificationResult(List<GateResult> gates) {

	public VerificationOutcome outcome() {
		if (gates.stream().anyMatch(gate -> gate.outcome() == VerificationOutcome.ERROR)) {
			return VerificationOutcome.ERROR;
		}
		if (gates.stream().anyMatch(gate -> gate.outcome() == VerificationOutcome.FAIL)) {
			return VerificationOutcome.FAIL;
		}
		return VerificationOutcome.PASS;
	}

	/** Candidate acceptance is possible only when this is true - AIW-143's own acceptance criteria. */
	public boolean passed() {
		return outcome() == VerificationOutcome.PASS;
	}
}
