package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiGatewayUnitTests {

	@Mock
	private ModelProfileResolver modelProfileResolver;

	@Test
	void dispatchesToTheProviderTheProfileResolvesTo() {
		when(modelProfileResolver.resolve("structured-reasoning")).thenReturn(new ResolvedModel("stub", "stub-model"));
		AiProvider stubProvider = stubProvider("stub", (request, model) -> new AiResponse("stub", model, "ok", request.correlationId()));
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of(stubProvider));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-1");
		AiResponse response = gateway.invoke(request);

		assertThat(response.provider()).isEqualTo("stub");
		assertThat(response.model()).isEqualTo("stub-model");
		assertThat(response.content()).isEqualTo("ok");
		assertThat(response.correlationId()).isEqualTo("corr-1");
	}

	@Test
	void throwsWhenTheResolvedProviderHasNoRegisteredBean() {
		when(modelProfileResolver.resolve("structured-reasoning")).thenReturn(new ResolvedModel("nonexistent", "x"));
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of());

		AiRequest request = new AiRequest("structured-reasoning", List.of(), 100, "corr-1");

		assertThatThrownBy(() -> gateway.invoke(request)).isInstanceOf(AiProviderNotConfiguredException.class);
	}

	@Test
	void normalizesAProviderFailureIntoAiGatewayException() {
		when(modelProfileResolver.resolve("structured-reasoning")).thenReturn(new ResolvedModel("stub", "stub-model"));
		AiProvider failingProvider = stubProvider("stub", (request, model) -> {
			throw new IllegalStateException("provider blew up");
		});
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of(failingProvider));

		AiRequest request = new AiRequest("structured-reasoning", List.of(), 100, "corr-1");

		assertThatThrownBy(() -> gateway.invoke(request))
				.isInstanceOf(AiGatewayException.class)
				.hasCauseInstanceOf(IllegalStateException.class);
	}

	private interface InvokeFn {
		AiResponse invoke(AiRequest request, String model);
	}

	private static AiProvider stubProvider(String name, InvokeFn fn) {
		return new AiProvider() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public AiResponse invoke(AiRequest request, String model) {
				return fn.invoke(request, model);
			}
		};
	}
}
