package ai.architech.backend.core.verification;

/**
 * Per-gate persisted status (AIW-155) - a strict superset of {@link VerificationOutcome}'s three
 * values. {@code SKIPPED} exists only here, never on the overall run: {@link
 * AuthoritativeRunnerVerifier}'s fail-fast design means a gate absent from a {@link
 * RunnerVerificationResult#gates()} list never ran at all, and persisting that absence
 * explicitly - rather than just omitting a row - is what makes "mandatory skipped gates cannot
 * yield final PASS" checkable from the persisted evidence itself.
 */
public enum GateStatus {
	PASS,
	FAIL,
	ERROR,
	SKIPPED;

	static GateStatus fromOutcome(VerificationOutcome outcome) {
		return switch (outcome) {
			case PASS -> PASS;
			case FAIL -> FAIL;
			case ERROR -> ERROR;
		};
	}
}
