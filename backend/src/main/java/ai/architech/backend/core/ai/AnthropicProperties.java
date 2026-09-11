package ai.architech.backend.core.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Anthropic API key, sourced from environment configuration only (see application.yml's
 * "real-ai" profile block and .env.example) - never committed, never hardcoded. Blank by
 * default so the platform stays runnable without it; {@link AnthropicProvider} is the only
 * place a blank key becomes an actual problem, and only when something actually tries to
 * route a model profile at "anthropic".
 *
 * <p>{@link #toString()} is overridden (AIW-64) so the key can never end up in a log line or
 * debug endpoint via this record's own default {@code toString()} - a real risk otherwise,
 * since records generate one automatically that prints every component verbatim.
 */
@ConfigurationProperties(prefix = "architech.ai.anthropic")
public record AnthropicProperties(String apiKey) {

	public AnthropicProperties {
		apiKey = apiKey == null ? "" : apiKey;
	}

	@Override
	public String toString() {
		return "AnthropicProperties[apiKey=" + (apiKey.isBlank() ? "<blank>" : "<redacted>") + "]";
	}
}
