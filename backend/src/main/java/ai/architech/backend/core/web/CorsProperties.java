package ai.architech.backend.core.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Real deployed environments (AIW-71) serve the frontend and backend from different origins
 * (an Azure Static Web App and a Container App, on different domains) - local dev doesn't need
 * this at all (Vite's own dev-server proxy keeps everything same-origin), so the empty default
 * below is deliberate: no CORS headers are added, and the browser's own same-origin policy
 * behaves exactly as it already does today, until an environment's deployment actually sets
 * this to that environment's real frontend origin.
 */
@ConfigurationProperties(prefix = "architech.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}
}
