package ai.architech.backend.core.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single place an exception becomes an HTTP response (AIW-59), replacing per-controller
 * {@code try/catch} and Spring Boot's default error page for anything thrown here. Every
 * response body is the same {@link ErrorResponse} shape, whether the failure was an {@link
 * ApplicationException} this platform raised on purpose, a leftover {@link
 * ResponseStatusException} from a call site not yet migrated to {@link ApplicationException},
 * or something genuinely unexpected.
 *
 * <p>{@link #handleUnexpected} is the safety net the acceptance criteria call for: it logs the
 * full exception (stack trace included) server-side for diagnosis, but the client only ever
 * sees a static, generic message under {@link ErrorCode#INTERNAL_ERROR} - never {@code
 * e.getMessage()}, which for a truly unanticipated failure (a {@code NullPointerException}, a
 * driver error, ...) could otherwise repeat internal implementation detail straight back to the
 * caller, exactly as happened once during this platform's own manual testing.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String GENERIC_INTERNAL_ERROR_MESSAGE = "An unexpected error occurred.";

	@ExceptionHandler(ApplicationException.class)
	ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException e, HttpServletRequest request) {
		return respond(e.errorCode().httpStatus(), e.errorCode().name(), e.getMessage(), request);
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
		String message = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
		return respond(status, "HTTP_" + status.value(), message, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), e);
		return respond(
				ErrorCode.INTERNAL_ERROR.httpStatus(), ErrorCode.INTERNAL_ERROR.name(), GENERIC_INTERNAL_ERROR_MESSAGE, request);
	}

	private static ResponseEntity<ErrorResponse> respond(
			HttpStatus status, String errorCode, String message, HttpServletRequest request) {
		String correlationId = MDC.get(RequestCorrelationFilter.MDC_KEY);
		ErrorResponse body = new ErrorResponse(errorCode, message, correlationId, Instant.now(), request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
