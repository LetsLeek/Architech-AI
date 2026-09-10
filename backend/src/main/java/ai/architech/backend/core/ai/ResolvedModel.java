package ai.architech.backend.core.ai;

/** {@code fallback}, when present, is itself a full {@link ResolvedModel} - see {@link AiGateway}, the only place it's consulted. */
public record ResolvedModel(String provider, String model, ResolvedModel fallback) {}
