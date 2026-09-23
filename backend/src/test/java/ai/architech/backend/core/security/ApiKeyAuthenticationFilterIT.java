package ai.architech.backend.core.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The one test in the suite that exercises {@link ApiKeyAuthenticationFilter} for real, through
 * the actual wired filter chain (default {@code addFilters}, unlike every other controller IT in
 * this project - see their own {@code addFilters = false} for why they're exempt). Every other
 * test asserts business behavior and would otherwise need to carry this filter's header as
 * unrelated noise; this is the one place the gate itself is proven end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyAuthenticationFilterIT {

	private static final String VALID_KEY = "architech-dev-api-key"; // matches application.yml's own default

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsAnApiRequestWithNoKey() throws Exception {
		mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsAnApiRequestWithTheWrongKey() throws Exception {
		mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID()).header("X-API-Key", "not-the-real-key"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void allowsAnApiRequestWithTheCorrectKey() throws Exception {
		// 404, not 401/403 - the request reached the real controller, which correctly reports
		// this random id as not found. Proves the gate lets a correctly-authenticated request
		// all the way through, not just that it stops short of rejecting it.
		mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID()).header("X-API-Key", VALID_KEY))
				.andExpect(status().isNotFound());
	}

	@Test
	void neverGatesTheActuatorHealthEndpoint() throws Exception {
		// No X-API-Key header at all - the deploy workflows' own post-deployment health check
		// (backend-smoke-test) calls this endpoint unauthenticated, before an operator would
		// have any chance to wire the key into that step.
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
