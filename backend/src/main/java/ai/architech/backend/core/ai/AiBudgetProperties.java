package ai.architech.backend.core.ai;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable soft/hard AI usage cost limits (AIW-65), checked by {@link
 * AiUsageBudgetGuard}. {@code perProject} applies uniformly to every project (a project's own
 * id can't be a static config key); {@code perAgent} is keyed by agent id, since the small,
 * known set of agent ids (e.g. "requirements-agent") - unlike project ids - genuinely is
 * config-time knowledge. Either limit can be {@code null} (no limit for that scope), and
 * within a {@link Limits}, either bound can independently be {@code null} - a scope can have
 * only a soft limit, only a hard limit, both, or (by omitting it from config entirely) neither.
 */
@ConfigurationProperties(prefix = "architech.ai.budget")
public record AiBudgetProperties(Map<String, Limits> perAgent, Limits perProject) {

	public AiBudgetProperties {
		perAgent = perAgent == null ? Map.of() : perAgent;
	}

	public Limits limitsForAgent(String agentId) {
		return perAgent.get(agentId);
	}

	public record Limits(BigDecimal softLimitUsd, BigDecimal hardLimitUsd) {}
}
