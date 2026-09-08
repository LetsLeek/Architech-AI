package ai.architech.backend.core.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Anthropic API key, sourced from environment configuration only (see application.yml's
 * "real-ai" profile block and .env.example) - never committed, never hardcoded. Blank by
 * default so the platform stays runnable without it; {@link AnthropicProvider} is the only
 * place a blank key becomes an actual problem, and only when something actually tries to
 * route a model profile at "anthropic".
 */
@ConfigurationProperties(prefix = "architech.ai.anthropic")
public record AnthropicProperties(String apiKey) {

	public AnthropicProperties {
		apiKey = apiKey == null ? "" : apiKey;
	}
}
