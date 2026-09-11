package ai.architech.backend.core.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Scoped to {@code /api/**} only - never {@code /actuator/**}, which a deployed environment's
 * own health-check caller (never a browser) reaches without needing CORS at all.
 */
@Configuration
class WebCorsConfig implements WebMvcConfigurer {

	private final CorsProperties properties;

	WebCorsConfig(CorsProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (properties.allowedOrigins().isEmpty()) {
			return;
		}
		registry
				.addMapping("/api/**")
				.allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
	}
}
