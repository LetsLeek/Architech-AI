package ai.architech.backend.core.error;

import org.springframework.http.HttpStatus;

/**
 * The stable, machine-readable identifier for one specific kind of business failure (AIW-59) -
 * a client can safely switch on {@link #name()} without depending on the human-readable message
 * ever staying the same. Adding a new business error is adding a constant here, never a new
 * {@link ApplicationException} subclass; the constant's {@link HttpStatus} is the single source
 * of truth for how that error maps onto the API, so {@link GlobalExceptionHandler} never needs
 * a per-error branch either.
 */
public enum ErrorCode {
	PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND),
	EVIDENCE_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND),
	UNSUPPORTED_PROJECT_TYPE(HttpStatus.BAD_REQUEST),
	PROJECT_HAS_NO_INPUT(HttpStatus.BAD_REQUEST),
	REQUIREMENTS_ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT),
	CANONICAL_ARTIFACT_NOT_FOUND(HttpStatus.NOT_FOUND),
	DESIGN_PROPOSAL_GENERATION_ALREADY_RUNNING(HttpStatus.CONFLICT),
	WEBSITE_GENERATION_ALREADY_RUNNING(HttpStatus.CONFLICT),
	DEVELOPER_EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND),
	QA_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND),
	QA_EXECUTION_INPUT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
	MODEL_RUNTIME_FAILURE(HttpStatus.BAD_GATEWAY),
	AI_BUDGET_HARD_LIMIT_EXCEEDED(HttpStatus.PAYMENT_REQUIRED),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus httpStatus;

	ErrorCode(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	public HttpStatus httpStatus() {
		return httpStatus;
	}
}
