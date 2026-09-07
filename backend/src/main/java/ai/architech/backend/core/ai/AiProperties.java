package ai.architech.backend.core.ai;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps logical model profiles (e.g. "structured-reasoning", as declared in an agent.yaml)
 * to a concrete provider+model. This is the only place that association lives - changing
 * which real model backs a profile means editing config here, never the frozen agent spec.
 */
@ConfigurationProperties(prefix = "architech.ai")
public record AiProperties(Map<String, ModelProfileConfig> modelProfiles) {

	public record ModelProfileConfig(String provider, String model) {}
}
