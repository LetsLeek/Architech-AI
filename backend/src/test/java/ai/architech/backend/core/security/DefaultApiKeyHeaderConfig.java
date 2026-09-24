package ai.architech.backend.core.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

/**
 * {@code @Import} this into any {@code @AutoConfigureMockMvc} controller IT (AIW-185) so its
 * {@code MockMvc} carries a valid {@code X-API-Key} header on every request by default - every
 * pre-existing controller IT is about its own business behavior, not
 * {@link ApiKeyAuthenticationFilter} itself (that gets its own dedicated
 * {@link ApiKeyAuthenticationFilterIT}, which deliberately does NOT import this).
 *
 * <p>Deliberately not {@code addFilters = false}: that flag disables every registered {@link
 * jakarta.servlet.Filter} bean, not just this one - including {@code RequestCorrelationFilter}
 * and {@code RequestLoggingFilter}, silently breaking any test that asserts on their behavior
 * (a correlation id, a log line). Supplying a valid default header instead keeps the real filter
 * chain - this one included - fully wired and exercised, exactly as it runs in production.
 */
@TestConfiguration
public class DefaultApiKeyHeaderConfig {

	// Matches application.yml's own architech.security.api-key default - never a value that
	// needs to be kept secret, since it's the same one committed there.
	static final String TEST_API_KEY = "architech-dev-api-key";

	@Bean
	MockMvcBuilderCustomizer defaultApiKeyHeaderCustomizer() {
		return (ConfigurableMockMvcBuilder<?> builder) ->
				builder.defaultRequest(MockMvcRequestBuilders.get("/").header("X-API-Key", TEST_API_KEY));
	}
}
