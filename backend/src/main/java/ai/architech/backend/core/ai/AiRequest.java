package ai.architech.backend.core.ai;

import java.util.List;

/**
 * A request to the AI Gateway. Callers name a logical {@code modelProfile} (as declared in
 * an agent.yaml) - never a concrete provider or model - plus a {@code correlationId} that
 * ties this call back to whatever AgentExecution it belongs to, for audit/telemetry.
 */
public record AiRequest(String modelProfile, List<AiMessage> messages, int maxOutputTokens, String correlationId) {}
