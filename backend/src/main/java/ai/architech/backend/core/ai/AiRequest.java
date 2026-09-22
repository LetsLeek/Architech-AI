package ai.architech.backend.core.ai;

import java.util.List;

/**
 * A request to the AI Gateway. Callers name a logical {@code modelProfile} (as declared in
 * an agent.yaml) - never a concrete provider or model - plus a {@code correlationId} that
 * ties this call back to whatever AgentExecution it belongs to, for audit/telemetry.
 *
 * <p>{@code tools} is additive (AIW-184): every existing call site uses the four-arg constructor
 * below, which leaves it empty - {@link AnthropicProvider} omits the {@code tools} field
 * entirely from the wire request in that case, identical to today's behavior. Only the new
 * multi-turn tool-calling loop ever supplies a non-empty list.
 */
public record AiRequest(String modelProfile, List<AiMessage> messages, int maxOutputTokens, String correlationId, List<ToolSchema> tools) {

	public AiRequest(String modelProfile, List<AiMessage> messages, int maxOutputTokens, String correlationId) {
		this(modelProfile, messages, maxOutputTokens, correlationId, List.of());
	}
}
