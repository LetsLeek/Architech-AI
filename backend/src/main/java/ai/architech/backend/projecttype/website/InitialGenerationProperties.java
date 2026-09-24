package ai.architech.backend.projecttype.website;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform-owned bounds for the A/B/C initial-generation fan-out (AIW-146) - "retry/correction
 * budgets are bounded and owned by Workflow/Core" (this ticket's own scope), never something the
 * Developer Agent or a single sibling execution gets to decide for itself. Mirrors {@code
 * core.runner.RunnerProperties}' own fail-fast-at-startup idiom.
 */
@ConfigurationProperties(prefix = "architech.developer.initial-generation")
public record InitialGenerationProperties(int maxCorrectionCyclesPerSibling, int maxSiblingRetries) {

	public InitialGenerationProperties {
		if (maxCorrectionCyclesPerSibling < 0) {
			throw new IllegalStateException(
					"architech.developer.initial-generation.max-correction-cycles-per-sibling must not be negative, was "
							+ maxCorrectionCyclesPerSibling);
		}
		if (maxSiblingRetries < 0) {
			throw new IllegalStateException(
					"architech.developer.initial-generation.max-sibling-retries must not be negative, was " + maxSiblingRetries);
		}
	}
}
