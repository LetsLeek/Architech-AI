package ai.architech.backend.core.verification;

/** One mandatory gate's structured, auditable evidence (AIW-143). {@code detail} is null on PASS. */
public record GateResult(String gateName, VerificationOutcome outcome, String detail) {

	public static GateResult pass(String gateName) {
		return new GateResult(gateName, VerificationOutcome.PASS, null);
	}

	public static GateResult fail(String gateName, String detail) {
		return new GateResult(gateName, VerificationOutcome.FAIL, detail);
	}

	public static GateResult error(String gateName, String detail) {
		return new GateResult(gateName, VerificationOutcome.ERROR, detail);
	}
}
