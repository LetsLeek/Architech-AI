package ai.architech.backend.core.ai;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real {@link AiProvider} backed by OpenAI's Chat Completions API
 * (<a href="https://platform.openai.com/docs/api-reference/chat">platform.openai.com/docs/api-reference/chat</a>).
 * Registered unconditionally, exactly like {@link MockAiProvider} and {@link
 * AnthropicProvider} - whether this is ever actually invoked depends entirely on which
 * provider a model profile names in {@code architech.ai.model-profiles}, a config-only
 * decision. Per AIW-62's own acceptance criteria, no model profile names "openai" by default
 * in this codebase - registering the bean and having real credentials configured is not the
 * same as anything actually routing to it. A missing/blank API key is only a problem the
 * moment this provider is actually called (see {@link #invoke}), never at startup.
 *
 * <p>Unlike Anthropic, OpenAI's Chat Completions API takes "system" as a regular message role
 * alongside "user" - no separate top-level field, so {@code request.messages()} maps directly
 * onto the request's {@code messages} array with no concatenation or reshaping.
 *
 * <p>Deliberately no try/catch here: any failure (network, 4xx/5xx from {@code RestClient}'s
 * default error handling, malformed response) propagates as a {@code RuntimeException}, which
 * {@link AiGateway#invoke} already normalizes into {@link AiGatewayException} with a static
 * message - provider-specific detail (e.g. a rate-limit or auth error body) never needs
 * special handling here to stay out of anything a client could see.
 *
 * <p>No markdown-fence or preamble normalization here (contrast {@link AnthropicProvider}) -
 * that workaround was added only after live testing actually observed Claude's specific
 * behavior; nothing here has been verified against a real OpenAI response yet, so nothing
 * speculative is added preemptively. Revisit if/when this provider is actually wired to a
 * model profile and exercised for real.
 */
@Component
class OpenAiProvider implements AiProvider {

	private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

	private final RestClient restClient;
	private final OpenAiProperties properties;
	private final ObjectMapper objectMapper;

	OpenAiProvider(RestClient.Builder restClientBuilder, OpenAiProperties properties, ObjectMapper objectMapper) {
		this.restClient = restClientBuilder.build();
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public String name() {
		return "openai";
	}

	@Override
	public AiResponse invoke(AiRequest request, String model) {
		if (properties.apiKey().isBlank()) {
			throw new AiProviderNotConfiguredException(name());
		}

		JsonNode responseBody = restClient
				.post()
				.uri(CHAT_COMPLETIONS_URL)
				.header("Authorization", "Bearer " + properties.apiKey())
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

		ArrayNode messages = body.putArray("messages");
		for (AiMessage message : request.messages()) {
			messages.addObject().put("role", message.role()).put("content", message.content());
		}
		return body;
	}

	private static AiResponse toAiResponse(JsonNode responseBody, String correlationId) {
		String text = responseBody.path("choices").path(0).path("message").path("content").asString();
		JsonNode usage = responseBody.path("usage");
		Integer promptTokens = intOrNull(usage.path("prompt_tokens"));
		Integer completionTokens = intOrNull(usage.path("completion_tokens"));
		String actualModel = responseBody.path("model").asString();
		return new AiResponse("openai", actualModel, text, correlationId, promptTokens, completionTokens, null, null);
	}

	private static Integer intOrNull(JsonNode node) {
		return node.isMissingNode() || node.isNull() ? null : node.asInt();
	}
}
