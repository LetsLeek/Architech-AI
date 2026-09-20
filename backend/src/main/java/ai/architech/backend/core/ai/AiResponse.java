package ai.architech.backend.core.ai;

import java.util.List;

/**
 * The provider/model that actually answered are recorded here, never assumed by the
 * caller. {@code promptTokens}/{@code completionTokens} are {@code null} when a provider
 * doesn't report usage - never fabricated as zero, since zero would falsely claim "confirmed
 * no tokens used" rather than "unknown". {@code promptTokens} is the non-cached portion only
 * (Anthropic's {@code usage.input_tokens}); {@code cacheCreationInputTokens}/{@code
 * cacheReadInputTokens} are the two cache-token counts a caching-aware provider reports
 * separately (see {@link AnthropicProvider}) - {@code null}, not zero, for a provider that
 * doesn't support caching at all.
 *
 * <p>{@code stopReason}/{@code blocks} are additive (AIW-184): every existing call site
 * constructs (or receives from a provider built before this ticket) an {@code AiResponse} via
 * the eight-arg constructor below, which defaults {@code stopReason} to {@code "end_turn"} and
 * {@code blocks} to empty - {@code response.content()} keeps meaning exactly what it always has,
 * the concatenated text of any {@code text} blocks. Only a response whose {@code stopReason} is
 * {@code "tool_use"} carries {@link ContentBlock.ToolUse} entries in {@code blocks} at all.
 */
public record AiResponse(
		String provider,
		String model,
		String content,
		String correlationId,
		Integer promptTokens,
		Integer completionTokens,
		Integer cacheCreationInputTokens,
		Integer cacheReadInputTokens,
		String stopReason,
		List<ContentBlock> blocks) {

	public AiResponse(
			String provider,
			String model,
			String content,
			String correlationId,
			Integer promptTokens,
			Integer completionTokens,
			Integer cacheCreationInputTokens,
			Integer cacheReadInputTokens) {
		this(
				provider, model, content, correlationId, promptTokens, completionTokens,
				cacheCreationInputTokens, cacheReadInputTokens, "end_turn", List.of());
	}

	public boolean requiresToolUse() {
		return "tool_use".equals(stopReason);
	}

	public List<ContentBlock.ToolUse> toolUses() {
		return blocks.stream().filter(ContentBlock.ToolUse.class::isInstance).map(ContentBlock.ToolUse.class::cast).toList();
	}
}
