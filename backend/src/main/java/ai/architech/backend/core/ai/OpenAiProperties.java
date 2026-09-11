package ai.architech.backend.core.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The OpenAI API key, sourced from environment configuration only (see application.yml and
 * .env.example) - never committed, never hardcoded. Blank by default so the platform stays
 * runnable without it; {@link OpenAiProvider} is the only place a blank key becomes an actual
 * problem, and only when something actually tries to route a model profile at "openai" - which,
 * per AIW-62, nothing does by default.
 */
@ConfigurationProperties(prefix = "architech.ai.openai")
public record OpenAiProperties(String apiKey) {

	public OpenAiProperties {
		apiKey = apiKey == null ? "" : apiKey;
	}

	/** Overridden (AIW-64, mirrors {@link AnthropicProperties}) so the key can never end up in a log line or debug endpoint via this record's own default {@code toString()}. */
	@Override
	public String toString() {
		return "OpenAiProperties[apiKey=" + (apiKey.isBlank() ? "<blank>" : "<redacted>") + "]";
	}
}
