package ai.architech.backend.core.ai;

/**
 * The provider/model that actually answered are recorded here, never assumed by the
 * caller. {@code promptTokens}/{@code completionTokens} are {@code null} when a provider
 * doesn't report usage - never fabricated as zero, since zero would falsely claim "confirmed
 * no tokens used" rather than "unknown". {@code promptTokens} is the non-cached portion only
 * (Anthropic's {@code usage.input_tokens}); {@code cacheCreationInputTokens}/{@code
 * cacheReadInputTokens} are the two cache-token counts a caching-aware provider reports
 * separately (see {@link AnthropicProvider}) - {@code null}, not zero, for a provider that
 * doesn't support caching at all.
 */
public record AiResponse(
		String provider,
		String model,
		String content,
		String correlationId,
		Integer promptTokens,
		Integer completionTokens,
		Integer cacheCreationInputTokens,
		Integer cacheReadInputTokens) {}
