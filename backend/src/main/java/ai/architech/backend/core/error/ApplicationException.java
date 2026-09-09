package ai.architech.backend.core.error;

/**
 * The one exception type business/domain code should throw for a failure that must reach an
 * API client as a structured, safe error (AIW-59) - replacing the previous pattern of a new
 * {@code RuntimeException} subclass per business error (see e.g. the now-removed {@code
 * EvidenceSnapshotNotFoundException}, {@code UnsupportedProjectTypeException}) and of ad-hoc
 * {@code ResponseStatusException} throws scattered across controllers. A new business error is
 * a new {@link ErrorCode} constant plus a {@code throw new ApplicationException(...)} at the
 * point of failure, never a new class or a new {@code @ExceptionHandler} branch.
 *
 * <p>{@link #getMessage()} is what {@link GlobalExceptionHandler} sends back to the client
 * verbatim - callers must only ever pass a message that is already safe to expose (no stack
 * traces, no secrets, no internal implementation detail), exactly as the codebase's existing
 * hand-written {@code ResponseStatusException} messages already did by convention; this class
 * just makes that convention structural instead of relying on every call site remembering it.
 */
public class ApplicationException extends RuntimeException {

	private final ErrorCode errorCode;

	public ApplicationException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ApplicationException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
