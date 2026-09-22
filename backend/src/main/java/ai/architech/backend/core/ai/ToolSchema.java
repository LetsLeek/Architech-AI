package ai.architech.backend.core.ai;

import tools.jackson.databind.JsonNode;

/** One tool's advertised name/description/JSON-Schema input shape (AIW-184) - the Anthropic Messages API's own {@code tools[]} entry shape, provider-agnostic at this layer. */
public record ToolSchema(String name, String description, JsonNode inputSchema) {}
