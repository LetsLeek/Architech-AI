package ai.architech.backend.core.error;

import java.time.Instant;

/**
 * The one API error response shape (AIW-59), returned by {@link GlobalExceptionHandler} for
 * every {@link ApplicationException} and every unexpected failure alike - a client never has to
 * guess which fields a given error will carry. {@code correlationId} is {@code null} only if
 * {@link RequestCorrelationFilter} somehow never ran for this request; it otherwise always
 * matches the {@value RequestCorrelationFilter#RESPONSE_HEADER} response header and the {@code
 * correlationId} value tagging this request's server-side log lines, so a report from a client
 * can be traced back to exactly what the server logged for it.
 */
public record ErrorResponse(String errorCode, String message, String correlationId, Instant timestamp, String path) {}
