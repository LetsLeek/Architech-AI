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
class OpenAiProviderTests {

	private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void sendsSystemAndUserMessagesDirectlyAndParsesTheResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OpenAiProvider provider = new OpenAiProvider(builder, new OpenAiProperties("test-key"), objectMapper);

		String responseJson =
				"""
				{
				  "model": "gpt-4o",
				  "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hello there"}}],
				  "usage": {"prompt_tokens": 42, "completion_tokens": 7}
				}
				""";
		// unlike Anthropic, "system" is just another entry in the same messages array - no
		// separate top-level field and no concatenation of multiple system messages.
		String expectedRequestJson =
				"""
				{
				  "model": "gpt-4o",
				  "max_tokens": 1000,
				  "messages": [
				    {"role": "system", "content": "Follow the rules."},
				    {"role": "user", "content": "Describe the business."}
				  ]
				}
				""";

		server.expect(requestTo(CHAT_COMPLETIONS_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-key"))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().json(expectedRequestJson, true))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest(
				"structured-reasoning",
				List.of(new AiMessage("system", "Follow the rules."), new AiMessage("user", "Describe the business.")),
				1000,
				"corr-1");

		AiResponse response = provider.invoke(request, "gpt-4o");

		assertThat(response.provider()).isEqualTo("openai");
		assertThat(response.model()).isEqualTo("gpt-4o");
		assertThat(response.content()).isEqualTo("Hello there");
		assertThat(response.correlationId()).isEqualTo("corr-1");
		assertThat(response.promptTokens()).isEqualTo(42);
		assertThat(response.completionTokens()).isEqualTo(7);
		assertThat(response.cacheCreationInputTokens()).isNull();
		assertThat(response.cacheReadInputTokens()).isNull();
		server.verify();
	}

	@Test
	void throwsWhenNoApiKeyIsConfiguredWithoutMakingAnyRequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer.bindTo(builder).build();
		OpenAiProvider provider = new OpenAiProvider(builder, new OpenAiProperties(""), objectMapper);

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-2");

		assertThatThrownBy(() -> provider.invoke(request, "gpt-4o")).isInstanceOf(AiProviderNotConfiguredException.class);
	}

	@Test
	void propagatesAsARuntimeExceptionOnANonSuccessResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OpenAiProvider provider = new OpenAiProvider(builder, new OpenAiProperties("test-key"), objectMapper);

		server.expect(requestTo(CHAT_COMPLETIONS_URL))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
						.body("{\"error\":{\"message\":\"invalid api key\"}}")
						.contentType(MediaType.APPLICATION_JSON));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-3");

		// this is exactly what AiGateway.invoke() relies on to normalize into AiGatewayException -
		// nothing here should swallow or wrap it into anything provider-specific
		assertThatThrownBy(() -> provider.invoke(request, "gpt-4o")).isInstanceOf(RuntimeException.class);
	}
}
