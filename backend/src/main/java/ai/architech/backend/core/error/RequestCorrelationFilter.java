package ai.architech.backend.core.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tags every request with a fresh correlation id (AIW-59) - in {@link MDC} for the duration of
 * the request (so every log line written while handling it carries the same id, whatever
 * logging pattern eventually reads {@value #MDC_KEY}), and on the response as {@value
 * #RESPONSE_HEADER} so a client can quote it back when reporting an issue. {@link
 * GlobalExceptionHandler} reads it back out of {@link MDC} to embed in {@link ErrorResponse} -
 * one id ties a client-visible error to its server-side log lines.
 *
 * <p>Always generated fresh, never trusted from an incoming header: an id a caller could set
 * themselves would let one client's requests spoof or collide with another's in the logs.
 *
 * <p>Ordered ahead of {@code RequestLoggingFilter} (AIW-77) so the correlation id is already in
 * {@link MDC} - and therefore in that filter's own structured log line - for every request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	// AIW-185: public (not package-private) so ApiKeyAuthenticationFilter (core.security) can
	// embed the same correlation id in its own 401 responses, rather than duplicating this
	// literal in a second package.
	public static final String MDC_KEY = "correlationId";
	static final String RESPONSE_HEADER = "X-Correlation-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String correlationId = UUID.randomUUID().toString();
		MDC.put(MDC_KEY, correlationId);
		response.setHeader(RESPONSE_HEADER, correlationId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
