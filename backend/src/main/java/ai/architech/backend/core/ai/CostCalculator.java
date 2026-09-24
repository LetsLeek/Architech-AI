package ai.architech.backend.core.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Computes AI usage cost from configured per-1000-token pricing (see
 * {@code architech.ai.pricing}). Returns {@code null} - never a fabricated number - whenever
 * the inputs don't actually support a real calculation: unknown token counts, or no pricing
 * configured for that provider/model.
 */
@Component
public class CostCalculator {

	private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);

	private final AiProperties properties;

	CostCalculator(AiProperties properties) {
		this.properties = properties;
	}

	public BigDecimal calculateUsd(
			String provider,
			String model,
			Integer promptTokens,
			Integer completionTokens,
			Integer cacheCreationInputTokens,
			Integer cacheReadInputTokens) {
		if (promptTokens == null || completionTokens == null) {
			return null;
		}

		ModelPricing pricing = lookup(provider, model);
		if (pricing == null) {
			return null;
		}

		BigDecimal cacheWriteCost = cacheTokenCost(pricing.cacheWriteCostPer1000Tokens(), cacheCreationInputTokens);
		BigDecimal cacheReadCost = cacheTokenCost(pricing.cacheReadCostPer1000Tokens(), cacheReadInputTokens);
		if (cacheWriteCost == null || cacheReadCost == null) {
			return null;
		}

		BigDecimal promptCost = perToken(pricing.promptCostPer1000Tokens(), promptTokens);
		BigDecimal completionCost = perToken(pricing.completionCostPer1000Tokens(), completionTokens);
		return promptCost.add(completionCost).add(cacheWriteCost).add(cacheReadCost);
	}

	private ModelPricing lookup(String provider, String model) {
		Map<String, ModelPricing> byModel = properties.pricing().get(provider);
		return byModel == null ? null : byModel.get(model);
	}

	/** {@code null} tokens (provider doesn't report caching) or zero tokens cost nothing, without requiring cache pricing to be configured at all. Nonzero tokens with no configured cache price return {@code null} - a real cost was incurred that this can't calculate, so it must not be silently priced at zero. */
	private static BigDecimal cacheTokenCost(BigDecimal costPer1000, Integer tokens) {
		if (tokens == null || tokens == 0) {
			return BigDecimal.ZERO;
		}
		if (costPer1000 == null) {
			return null;
		}
		return perToken(costPer1000, tokens);
	}

	private static BigDecimal perToken(BigDecimal costPer1000, int tokens) {
		return costPer1000.multiply(BigDecimal.valueOf(tokens)).divide(ONE_THOUSAND, 6, RoundingMode.HALF_UP);
	}
}
