package ai.architech.backend.core.ai;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real {@link AiProvider} backed by Anthropic's Messages API
 * (<a href="https://docs.anthropic.com/en/api/messages">docs.anthropic.com/en/api/messages</a>).
 * Registered unconditionally, exactly like {@link MockAiProvider} - whether this is ever
 * actually invoked depends entirely on which provider a model profile names in
 * {@code architech.ai.model-profiles}, a config-only decision (see application.yml's
 * "real-ai" Spring profile). A missing/blank API key is only a problem the moment this
 * provider is actually called (see {@link #invoke}), never at startup: the platform must
 * stay runnable on the mock provider regardless of whether Anthropic credentials exist.
 *
 * <p>Anthropic's Messages API takes "system" as a distinct top-level request field, not a
 * message with role "system" - {@code messages} here only ever contains role
 * "system"/"user" (see {@code AgentRunner.buildMessages}), so any "system" entries are
 * concatenated into that top-level field and everything else becomes a real message.
 *
 * <p>Deliberately no try/catch here: any failure (network, 4xx/5xx from
 * {@code RestClient}'s default error handling, malformed response) propagates as a
 * {@code RuntimeException}, which {@link AiGateway#invoke} already normalizes into
 * {@link AiGatewayException} with a static message - provider-specific detail (e.g. what a
 * real error body said) never needs special handling here to stay out of anything a client
 * could see.
 *
 * <p>Extracts the substring between the first {@code {} and the last {@code }} from the response
 * text, discarding everything outside it. Verified against the real API during AIW-127: despite
 * prompt instructions to output raw JSON only, this model (claude-sonnet-5) reliably wraps
 * structured output in a {@code ```json ... ```} markdown code fence anyway, sometimes preceded
 * by a conversational preamble ({@code "Looking at this evidence, I'll extract..."}) before the
 * fence even starts - and, unlike some older Claude models, it rejects the classic "assistant
 * message prefill" workaround outright ("This model does not support assistant message
 * prefill"). Un-wrapping a known, provider-specific wire-format artifact here is transport
 * handling, not content repair - the deterministic validators downstream still see (and reject,
 * if warranted) whatever JSON was actually inside the braces, unmodified.
 *
 * <p>Sends {@code "thinking": {"type": "disabled"}} on every request. Also discovered during
 * AIW-127: this model uses extended thinking by default even though nothing in this request
 * asks for it, and the thinking tokens are drawn from the same {@code max_tokens} budget as the
 * actual output - on one real run, thinking alone consumed 6926 of an 8000-token budget, cutting
 * the JSON output off mid-object ({@code stop_reason: "max_tokens"}). Requirements Analysis
 * output is single-shot deterministic JSON per an explicit schema; there is no reasoning benefit
 * here worth budgeting for, so thinking is switched off outright rather than compensated for by
 * inflating {@code maxOutputTokens}.
 *
 * <p>Marks the concatenated system prompt as cacheable (AIW-129): {@code system} is sent as a
 * single-block content array with {@code cache_control: {type: "ephemeral"}} on that block,
 * rather than a plain string - Anthropic only supports cache breakpoints on the block form.
 * The system prompt (agent role, rules, skills, both full output schemas) is ~13k tokens and
 * identical across every call for a given agent version; only the evidence in the user message
 * changes. Verified against the real API: an identical second call re-used the cached prefix
 * ({@code usage.cache_read_input_tokens} matched the first call's {@code
 * cache_creation_input_tokens}) instead of paying full input-token price again.
 */
@Component
class AnthropicProvider implements AiProvider {

	private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
	private static final String ANTHROPIC_VERSION = "2023-06-01";

	private final RestClient restClient;
	private final AnthropicProperties properties;
	private final ObjectMapper objectMapper;

	AnthropicProvider(RestClient.Builder restClientBuilder, AnthropicProperties properties, ObjectMapper objectMapper) {
		this.restClient = restClientBuilder.build();
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public String name() {
		return "anthropic";
	}

	@Override
	public AiResponse invoke(AiRequest request, String model) {
		if (properties.apiKey().isBlank()) {
			throw new AiProviderNotConfiguredException(name());
		}

		JsonNode responseBody = restClient
				.post()
				.uri(MESSAGES_URL)
				.header("x-api-key", properties.apiKey())
				.header("anthropic-version", ANTHROPIC_VERSION)
				.contentType(MediaType.APPLICATION_JSON)
				.body(buildRequestBody(request, model))
				.retrieve()
				.body(JsonNode.class);

		return toAiResponse(responseBody, request.correlationId());
	}

	private ObjectNode buildRequestBody(AiRequest request, String model) {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", request.maxOutputTokens());
		body.putObject("thinking").put("type", "disabled");

		StringBuilder system = new StringBuilder();
		ArrayNode messages = body.putArray("messages");
		for (AiMessage message : request.messages()) {
			if ("system".equals(message.role())) {
				if (!system.isEmpty()) {
					system.append("\n\n");
				}
				system.append(message.content());
			} else {
				messages.addObject().put("role", message.role()).put("content", message.content());
			}
		}
		if (!system.isEmpty()) {
			ObjectNode systemBlock =
					body.putArray("system").addObject().put("type", "text").put("text", system.toString());
			systemBlock.putObject("cache_control").put("type", "ephemeral");
		}
		return body;
	}

	private static AiResponse toAiResponse(JsonNode responseBody, String correlationId) {
		String text = extractJsonObject(extractText(responseBody.path("content")));
		JsonNode usage = responseBody.path("usage");
		Integer promptTokens = intOrNull(usage.path("input_tokens"));
		Integer completionTokens = intOrNull(usage.path("output_tokens"));
		Integer cacheCreationInputTokens = intOrNull(usage.path("cache_creation_input_tokens"));
		Integer cacheReadInputTokens = intOrNull(usage.path("cache_read_input_tokens"));
		String actualModel = responseBody.path("model").asString();
		return new AiResponse(
				"anthropic",
				actualModel,
				text,
				correlationId,
				promptTokens,
				completionTokens,
				cacheCreationInputTokens,
				cacheReadInputTokens);
	}

	private static String extractText(JsonNode contentBlocks) {
		StringBuilder text = new StringBuilder();
		if (contentBlocks.isArray()) {
			for (JsonNode block : contentBlocks) {
				if ("text".equals(block.path("type").asString())) {
					text.append(block.path("text").asString());
				}
			}
		}
		return text.toString();
	}

	/**
	 * Slices from the first {@code {} to the last {@code }} (inclusive), discarding any
	 * preamble, markdown fence, or trailing commentary around it. Falls back to the original
	 * text untouched if no {@code {} is found at all, so a genuinely non-JSON response still
	 * surfaces its own parse error downstream instead of being silently mangled.
	 */
	private static String extractJsonObject(String text) {
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start == -1 || end < start) {
			return text;
		}
		return text.substring(start, end + 1);
	}

	private static Integer intOrNull(JsonNode node) {
		return node.isMissingNode() || node.isNull() ? null : node.asInt();
	}
}
