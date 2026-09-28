package ai.architech.backend.projecttype.website;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform-owned bound (AIW-216) for the Designer Agent's own feedback-correction loop -
 * "retry/correction budgets are bounded and owned by Workflow/Core", the same reasoning {@link
 * InitialGenerationProperties} already gives, never something the Designer Agent gets to decide
 * for itself. Kept conservative by default: each retry is a real paid model call with a strictly
 * larger prompt than the original attempt (it also carries the full prior output), so this isn't
 * a knob to raise casually. Mirrors {@code core.runner.RunnerProperties}'s own
 * fail-fast-at-startup idiom.
 */
@ConfigurationProperties(prefix = "architech.designer")
public record DesignerFeedbackRetryProperties(int maxFeedbackRetries) {

	public DesignerFeedbackRetryProperties {
		if (maxFeedbackRetries < 0) {
			throw new IllegalStateException(
					"architech.designer.max-feedback-retries must not be negative, was " + maxFeedbackRetries);
		}
	}
}
