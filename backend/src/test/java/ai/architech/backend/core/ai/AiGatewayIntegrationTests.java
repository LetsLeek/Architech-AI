package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiGatewayIntegrationTests {

	@Autowired
	private AiGateway gateway;

	@Test
	void invokesTheConfiguredMockProviderThroughTheRealSpringWiring() {
		AiRequest request = new AiRequest(
				"structured-reasoning", List.of(new AiMessage("user", "hello")), 500, "corr-integration");

		AiResponse response = gateway.invoke(request);

		assertThat(response.provider()).isEqualTo("mock");
		assertThat(response.model()).isEqualTo("mock-model");
		assertThat(response.correlationId()).isEqualTo("corr-integration");
		assertThat(response.promptTokens()).isNull();
		assertThat(response.completionTokens()).isNull();
	}
}
