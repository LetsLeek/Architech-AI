package ai.architech.backend.core.ai;

import java.util.List;

/**
 * {@code blocks} is additive (AIW-184): every existing plain-text call site constructs an
 * {@code AiMessage} via the two-arg constructor below, which leaves {@code blocks} empty - {@link
 * AnthropicProvider} sends such a message exactly as it always has (a plain string {@code
 * content}). A non-empty {@code blocks} list (an assistant turn replaying its own prior {@link
 * ContentBlock.Text}/{@link ContentBlock.ToolUse} blocks, or a user turn submitting {@link
 * ContentBlock.ToolResult}s) is only ever produced by the new multi-turn tool-calling loop.
 */
public record AiMessage(String role, String content, List<ContentBlock> blocks) {

	public AiMessage(String role, String content) {
		this(role, content, List.of());
	}

	public AiMessage(String role, List<ContentBlock> blocks) {
		this(role, null, blocks);
	}
}
