package ai.architech.backend.core.ai;

import java.math.BigDecimal;

/**
 * {@code cacheWriteCostPer1000Tokens}/{@code cacheReadCostPer1000Tokens} price Anthropic-style
 * prompt-cache tokens (see {@link AnthropicProvider}), which cost more than a regular prompt
 * token to write into the cache and much less to read back out of it. Both are {@code null}
 * when a model's config predates caching support - {@link CostCalculator} then refuses to
 * fabricate a total whenever a response actually reports nonzero cache token usage against
 * that model.
 */
public record ModelPricing(
		BigDecimal promptCostPer1000Tokens,
		BigDecimal completionCostPer1000Tokens,
		BigDecimal cacheWriteCostPer1000Tokens,
		BigDecimal cacheReadCostPer1000Tokens) {}
