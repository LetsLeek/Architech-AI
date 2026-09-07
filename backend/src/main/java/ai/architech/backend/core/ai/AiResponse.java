package ai.architech.backend.core.ai;

/**
 * The provider/model that actually answered are recorded here, never assumed by the
 * caller. {@code promptTokens}/{@code completionTokens} are {@code null} when a provider
 * doesn't report usage - never fabricated as zero, since zero would falsely claim "confirmed
 * no tokens used" rather than "unknown".
 */
public record AiResponse(
		String provider, String model, String content, String correlationId, Integer promptTokens, Integer completionTokens) {}
