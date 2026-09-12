package ai.architech.backend.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs exactly one structured line per request (AIW-77): method, path, status and duration -
 * the fields {@code azure-environment-architecture.md}'s monitoring section and the
 * "monitoring-alerts" Terraform module both rely on being queryable in Log Analytics. Ordered
 * after {@link ai.architech.backend.core.error.RequestCorrelationFilter} so this line always
 * carries the same {@code correlationId} as every other log line from the same request.
 *
 * <p>Deliberately logs only these four fields - never a header, a query string, or a body. There
 * is no redaction step here because there is nothing here to redact: an {@code Authorization}
 * header or an API key can't leak from a log line that never reads it in the first place.
 */
@Component
@Order(1)
class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long startNanos = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
			String method = request.getMethod();
			String path = request.getRequestURI();
			int status = response.getStatus();

			MDC.put("httpMethod", method);
			MDC.put("httpPath", path);
			MDC.put("httpStatus", String.valueOf(status));
			MDC.put("durationMs", String.valueOf(durationMs));
			try {
				log.info("{} {} -> {} ({} ms)", method, path, status, durationMs);
			} finally {
				MDC.remove("httpMethod");
				MDC.remove("httpPath");
				MDC.remove("httpStatus");
				MDC.remove("durationMs");
			}
		}
	}
}
