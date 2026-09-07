package ai.architech.backend.core.ai;

import java.math.BigDecimal;

public record ModelPricing(BigDecimal promptCostPer1000Tokens, BigDecimal completionCostPer1000Tokens) {}
