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

	public BigDecimal calculateUsd(String provider, String model, Integer promptTokens, Integer completionTokens) {
		if (promptTokens == null || completionTokens == null) {
			return null;
		}

		ModelPricing pricing = lookup(provider, model);
		if (pricing == null) {
			return null;
		}

		BigDecimal promptCost = perToken(pricing.promptCostPer1000Tokens(), promptTokens);
		BigDecimal completionCost = perToken(pricing.completionCostPer1000Tokens(), completionTokens);
		return promptCost.add(completionCost);
	}

	private ModelPricing lookup(String provider, String model) {
		Map<String, ModelPricing> byModel = properties.pricing().get(provider);
		return byModel == null ? null : byModel.get(model);
	}

	private static BigDecimal perToken(BigDecimal costPer1000, int tokens) {
		return costPer1000.multiply(BigDecimal.valueOf(tokens)).divide(ONE_THOUSAND, 6, RoundingMode.HALF_UP);
	}
}
