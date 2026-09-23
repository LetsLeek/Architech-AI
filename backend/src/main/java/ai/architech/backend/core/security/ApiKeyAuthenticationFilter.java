package ai.architech.backend.core.security;

import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.error.ErrorResponse;
import ai.architech.backend.core.error.RequestCorrelationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Gates every {@code /api/**} request behind one shared secret (AIW-185) - this backend had zero
 * authentication before this filter existed, reachable by anyone on the open internet. This is
 * deliberately a shared-secret gate, not per-customer authentication: there is no {@code User}
 * entity, no login flow, and {@code Project} still has no owner column. It stops casual/automated
 * access to the open internet, matching this project's current single-operator posture - it does
 * not stop a determined visitor who already has the real frontend page open in a browser, since
 * the same key necessarily ships inside that public page's own JS bundle (see {@code
 * frontend/src/api/http.ts}). Real multi-tenant authentication (accounts, ownership checks on
 * every existing endpoint) is a separate, much larger future epic, not attempted here.
 *
 * <p>{@code /actuator/**} is deliberately exempt - the DEV/STAGING/PROD deploy workflows' own
 * post-deployment health check ({@code .github/actions/backend-smoke-test}) calls it directly,
 * unauthenticated, before any operator would have a chance to pass this key into that step.
 *
 * <p>Ordered after {@link RequestCorrelationFilter} and {@code RequestLoggingFilter} so a
 * rejected request still carries a correlation id and still gets logged - an auth failure is not
 * exempt from the platform's own observability.
 */
@Component
@Order(2)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private static final String HEADER_NAME = "X-API-Key";
	private static final String PROTECTED_PATH_PREFIX = "/api/";

	private final ApiKeyProperties properties;
	private final ObjectMapper objectMapper;

	ApiKeyAuthenticationFilter(ApiKeyProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!request.getRequestURI().startsWith(PROTECTED_PATH_PREFIX) || isAuthorized(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		respondUnauthorized(request, response);
	}

	private boolean isAuthorized(HttpServletRequest request) {
		String providedKey = request.getHeader(HEADER_NAME);
		if (providedKey == null) {
			return false;
		}
		// MessageDigest.isEqual is constant-time by design (unlike String#equals) - a shared
		// secret gate should not leak how many leading characters an attacker's guess got right
		// via a timing side channel, however impractical exploiting that would be here.
		return MessageDigest.isEqual(
				providedKey.getBytes(StandardCharsets.UTF_8), properties.apiKey().getBytes(StandardCharsets.UTF_8));
	}

	private void respondUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String correlationId = MDC.get(RequestCorrelationFilter.MDC_KEY);
		ErrorResponse body = new ErrorResponse(
				ErrorCode.UNAUTHORIZED.name(),
				"A valid " + HEADER_NAME + " header is required.",
				correlationId,
				Instant.now(),
				request.getRequestURI());
		response.setStatus(ErrorCode.UNAUTHORIZED.httpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
