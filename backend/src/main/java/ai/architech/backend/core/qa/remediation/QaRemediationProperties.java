package ai.architech.backend.core.qa.remediation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform-owned bound for Developer remediation cycles per (project, stage, Variant Lineage)
 * (AIW-181) - "Each automatic category has finite policy-controlled bounds," decided by Workflow/
 * Core, never by the Developer Agent or a single QA execution. Mirrors {@code
 * InitialGenerationProperties}'s own fail-fast-at-startup idiom. Deliberately does not also bound
 * QA execution retries - that remains the platform's own generic {@code
 * architech.runner.max-attempts} ({@code RunnerProperties}), a distinct budget QA execution
 * retries alone consume.
 */
@ConfigurationProperties(prefix = "architech.qa.remediation")
public record QaRemediationProperties(int maxRemediationCyclesPerStage) {

	public QaRemediationProperties {
		if (maxRemediationCyclesPerStage < 0) {
			throw new IllegalStateException(
					"architech.qa.remediation.max-remediation-cycles-per-stage must not be negative, was " + maxRemediationCyclesPerStage);
		}
	}
}
