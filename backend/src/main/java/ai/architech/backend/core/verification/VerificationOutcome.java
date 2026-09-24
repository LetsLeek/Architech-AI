package ai.architech.backend.core.verification;

/**
 * The three outcomes AIW-143's own acceptance criteria names for every mandatory gate and for
 * the overall verification: {@code PASS}, {@code FAIL} (a technical source/config problem -
 * Developer-owned), and {@code ERROR} (a Runner/Sandbox/infrastructure malfunction, never a
 * Developer-owned defect - see how {@code AgentExecution.error} vs. {@code .fail} already draw
 * this exact line elsewhere in this codebase).
 */
public enum VerificationOutcome {
	PASS,
	FAIL,
	ERROR
}
