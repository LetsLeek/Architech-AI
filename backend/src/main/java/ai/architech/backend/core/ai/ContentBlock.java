package ai.architech.backend.core.ai;

import tools.jackson.databind.JsonNode;

/**
 * One block of an Anthropic-style multi-turn tool-calling message (AIW-184) - additive to the
 * plain-string {@link AiMessage#content()}/{@link AiResponse#content()} shape M1/M2 already use,
 * never a replacement for it. A message/response with no blocks at all is still exactly today's
 * plain-text shape; blocks only appear once a {@link AiRequest#tools()}-bearing multi-turn loop
 * is actually in progress.
 */
public sealed interface ContentBlock {

	/** A plain text segment - the same content a plain-text response already carries, just addressable as one block among others. */
	record Text(String text) implements ContentBlock {}

	/** The model requesting one tool call - {@code id} is Anthropic's own opaque call id, echoed back unchanged in the matching {@link ToolResult}. */
	record ToolUse(String id, String name, JsonNode input) implements ContentBlock {}

	/** The dispatch outcome of one prior {@link ToolUse}, fed back as part of the next request's user turn. */
	record ToolResult(String toolUseId, String content, boolean isError) implements ContentBlock {}
}
