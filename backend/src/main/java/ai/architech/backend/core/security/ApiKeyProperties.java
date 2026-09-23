package ai.architech.backend.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The one shared secret that gates every {@code /api/**} request (AIW-185) - a real, non-secret
 * default ({@code apiKey}'s own default in {@code application.yml}) keeps local dev/tests/CI
 * working with zero setup, exactly like {@code spring.datasource.password}'s own {@code
 * architech} fallback; every real DEV/STAGING/PROD value overrides it via Key Vault, never a
 * plain env var literal. This is deliberately a single shared-secret gate, not per-customer
 * authentication - see {@link ApiKeyAuthenticationFilter}'s own class docs for the full scope
 * decision.
 */
@ConfigurationProperties(prefix = "architech.security")
public record ApiKeyProperties(String apiKey) {

	public ApiKeyProperties {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("architech.security.api-key must not be blank");
		}
	}
}
