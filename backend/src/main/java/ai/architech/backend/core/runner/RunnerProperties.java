package ai.architech.backend.core.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds retry behavior platform-wide - not something an agent gets to decide for itself.
 * Fails fast at startup on a nonsensical value rather than silently allowing zero attempts.
 */
@ConfigurationProperties(prefix = "architech.runner")
public record RunnerProperties(int maxAttempts) {

	public RunnerProperties {
		if (maxAttempts < 1) {
			throw new IllegalStateException("architech.runner.max-attempts must be at least 1, was " + maxAttempts);
		}
	}
}
