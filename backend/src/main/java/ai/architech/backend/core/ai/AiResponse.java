package ai.architech.backend.core.ai;

/** The provider/model that actually answered are recorded here, never assumed by the caller. */
public record AiResponse(String provider, String model, String content, String correlationId) {}
