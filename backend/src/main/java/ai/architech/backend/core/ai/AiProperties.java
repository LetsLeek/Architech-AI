package ai.architech.backend.core.ai;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps logical model profiles (e.g. "structured-reasoning", as declared in an agent.yaml)
 * to a concrete provider+model. This is the only place that association lives - changing
 * which real model backs a profile means editing config here, never the frozen agent spec.
 *
 * <p>{@code pricing} is keyed provider -> model -> per-1000-token cost. A missing entry is
 * not an error - it just means cost can't be calculated for that provider/model, which
 * {@link CostCalculator} reports as "unknown" (null) rather than fabricating a number.
 */
@ConfigurationProperties(prefix = "architech.ai")
public record AiProperties(Map<String, ModelProfileConfig> modelProfiles, Map<String, Map<String, ModelPricing>> pricing) {

	public AiProperties {
		modelProfiles = modelProfiles == null ? Map.of() : modelProfiles;
		pricing = pricing == null ? Map.of() : pricing;
	}

	public record ModelProfileConfig(String provider, String model) {}
}
