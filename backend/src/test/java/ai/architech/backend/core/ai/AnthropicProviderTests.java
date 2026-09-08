package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Plain unit tests, no Spring context - a real HTTP call is never made; MockRestServiceServer intercepts it instead. */
class AnthropicProviderTests {

	private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void sendsSystemMessageSeparatelyFromUserMessagesAndParsesTheResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties("test-key"), objectMapper);

		String responseJson =
				"""
				{
				  "model": "claude-sonnet-5",
				  "content": [{"type": "text", "text": "Hello there"}],
				  "usage": {"input_tokens": 42, "output_tokens": 7}
				}
				""";
		String expectedRequestJson =
				"""
				{
				  "model": "claude-sonnet-5",
				  "max_tokens": 1000,
				  "thinking": {"type": "disabled"},
				  "system": "Follow the rules.",
				  "messages": [{"role": "user", "content": "Describe the business."}]
				}
				""";

		server.expect(requestTo(MESSAGES_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-api-key", "test-key"))
				.andExpect(header("anthropic-version", "2023-06-01"))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().json(expectedRequestJson, true))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest(
				"structured-reasoning",
				List.of(new AiMessage("system", "Follow the rules."), new AiMessage("user", "Describe the business.")),
				1000,
				"corr-1");

		AiResponse response = provider.invoke(request, "claude-sonnet-5");

		assertThat(response.provider()).isEqualTo("anthropic");
		assertThat(response.model()).isEqualTo("claude-sonnet-5");
		assertThat(response.content()).isEqualTo("Hello there");
		assertThat(response.correlationId()).isEqualTo("corr-1");
		assertThat(response.promptTokens()).isEqualTo(42);
		assertThat(response.completionTokens()).isEqualTo(7);
		server.verify();
	}

	@Test
	void concatenatesMultipleSystemMessagesAndOmitsAnEmptySystemField() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties("test-key"), objectMapper);

		String responseJson =
				"""
				{"model": "claude-sonnet-5", "content": [], "usage": {"input_tokens": 1, "output_tokens": 1}}
				""";

		// no system-role message at all - "system" must be entirely absent, not an empty string
		server.expect(requestTo(MESSAGES_URL))
				.andExpect(content()
						.json(
								"{\"model\":\"claude-sonnet-5\",\"max_tokens\":10,\"thinking\":{\"type\":\"disabled\"},\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
								true))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 10, "corr-2");

		provider.invoke(request, "claude-sonnet-5");

		server.verify();
	}

	@Test
	void stripsAJsonMarkdownCodeFenceFromTheResponseText() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties("test-key"), objectMapper);

		// the "text" value is the JSON string ```json\n{"customer-profile": {}}\n``` - written
		// as a fully-escaped JSON string literal so the response body itself is valid JSON
		String responseJson =
				"{\"model\": \"claude-sonnet-5\", \"content\": [{\"type\": \"text\", "
						+ "\"text\": \"```json\\n{\\\"customer-profile\\\": {}}\\n```\"}], "
						+ "\"usage\": {\"input_tokens\": 1, \"output_tokens\": 1}}";

		server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-5");

		AiResponse response = provider.invoke(request, "claude-sonnet-5");

		assertThat(response.content()).isEqualTo("{\"customer-profile\": {}}");
	}

	@Test
	void stripsAConversationalPreambleBeforeAFencedJsonBlock() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties("test-key"), objectMapper);

		// the "text" value is the JSON string
		// "Looking at this evidence, I'll extract the facts.\n\n```json\n{"customer-profile": {}}\n```"
		String responseJson =
				"{\"model\": \"claude-sonnet-5\", \"content\": [{\"type\": \"text\", "
						+ "\"text\": \"Looking at this evidence, I'll extract the facts.\\n\\n```json\\n{\\\"customer-profile\\\": {}}\\n```\"}], "
						+ "\"usage\": {\"input_tokens\": 1, \"output_tokens\": 1}}";

		server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-7");

		AiResponse response = provider.invoke(request, "claude-sonnet-5");

		assertThat(response.content()).isEqualTo("{\"customer-profile\": {}}");
	}

	@Test
	void leavesUnfencedTextUntouched() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties("test-key"), objectMapper);

		String responseJson =
				"""
				{"model": "claude-sonnet-5", "content": [{"type": "text", "text": "{\\"customer-profile\\": {}}"}], "usage": {"input_tokens": 1, "output_tokens": 1}}
				""";

		server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-6");

		AiResponse response = provider.invoke(request, "claude-sonnet-5");

		assertThat(response.content()).isEqualTo("{\"customer-profile\": {}}");
	}

	@Test
	void leavesTextWithNoBracesAtAllUntouched() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties("test-key"), objectMapper);

		String responseJson =
				"""
				{"model": "claude-sonnet-5", "content": [{"type": "text", "text": "I could not find any facts to extract."}], "usage": {"input_tokens": 1, "output_tokens": 1}}
				""";

		server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-8");

		AiResponse response = provider.invoke(request, "claude-sonnet-5");

		assertThat(response.content()).isEqualTo("I could not find any facts to extract.");
	}

	@Test
	void throwsWhenNoApiKeyIsConfiguredWithoutMakingAnyRequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties(""), objectMapper);

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-3");

		assertThatThrownBy(() -> provider.invoke(request, "claude-sonnet-5")).isInstanceOf(AiProviderNotConfiguredException.class);
	}

	@Test
	void propagatesAsARuntimeExceptionOnANonSuccessResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AnthropicProvider provider = new AnthropicProvider(builder, new AnthropicProperties("test-key"), objectMapper);

		server.expect(requestTo(MESSAGES_URL))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
						.body("{\"error\":\"invalid api key\"}")
						.contentType(MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-4");

		// this is exactly what AiGateway.invoke() relies on to normalize into AiGatewayException -
		// nothing here should swallow or wrap it into anything provider-specific
		assertThatThrownBy(() -> provider.invoke(request, "claude-sonnet-5")).isInstanceOf(RuntimeException.class);
	}
}
