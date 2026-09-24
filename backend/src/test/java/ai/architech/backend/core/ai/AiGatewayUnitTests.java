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
		when(modelProfileResolver.resolve("structured-reasoning"))
				.thenReturn(new ResolvedModel("stub", "stub-model", null));
		AiProvider stubProvider = stubProvider(
				"stub", (request, model) -> new AiResponse("stub", model, "ok", request.correlationId(), 10, 20, null, null));
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of(stubProvider));

		AiRequest request = new AiRequest("structured-reasoning", List.of(new AiMessage("user", "hi")), 100, "corr-1");
		AiResponse response = gateway.invoke(request);

		assertThat(response.provider()).isEqualTo("stub");
		assertThat(response.model()).isEqualTo("stub-model");
		assertThat(response.content()).isEqualTo("ok");
		assertThat(response.correlationId()).isEqualTo("corr-1");
		assertThat(response.promptTokens()).isEqualTo(10);
		assertThat(response.completionTokens()).isEqualTo(20);
	}

	@Test
	void throwsWhenTheResolvedProviderHasNoRegisteredBean() {
		when(modelProfileResolver.resolve("structured-reasoning")).thenReturn(new ResolvedModel("nonexistent", "x", null));
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of());

		AiRequest request = new AiRequest("structured-reasoning", List.of(), 100, "corr-1");

		assertThatThrownBy(() -> gateway.invoke(request)).isInstanceOf(AiProviderNotConfiguredException.class);
	}

	@Test
	void normalizesAProviderFailureIntoAiGatewayException() {
		when(modelProfileResolver.resolve("structured-reasoning"))
				.thenReturn(new ResolvedModel("stub", "stub-model", null));
		AiProvider failingProvider = stubProvider("stub", (request, model) -> {
			throw new IllegalStateException("provider blew up");
		});
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of(failingProvider));

		AiRequest request = new AiRequest("structured-reasoning", List.of(), 100, "corr-1");

		assertThatThrownBy(() -> gateway.invoke(request))
				.isInstanceOf(AiGatewayException.class)
				.hasCauseInstanceOf(IllegalStateException.class);
	}

	@Test
	void fallsBackToTheConfiguredProviderWhenThePrimaryFails() {
		when(modelProfileResolver.resolve("structured-reasoning"))
				.thenReturn(new ResolvedModel("primary", "primary-model", new ResolvedModel("fallback", "fallback-model", null)));
		AiProvider failingPrimary = stubProvider("primary", (request, model) -> {
			throw new IllegalStateException("primary blew up");
		});
		AiProvider fallbackProvider = stubProvider(
				"fallback",
				(request, model) -> new AiResponse("fallback", model, "from fallback", request.correlationId(), 5, 5, null, null));
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of(failingPrimary, fallbackProvider));

		AiRequest request = new AiRequest("structured-reasoning", List.of(), 100, "corr-1");
		AiResponse response = gateway.invoke(request);

		// the fallback's own response comes back unmodified - which provider actually answered
		// is never hidden (AIW-66 AC).
		assertThat(response.provider()).isEqualTo("fallback");
		assertThat(response.model()).isEqualTo("fallback-model");
		assertThat(response.content()).isEqualTo("from fallback");
	}

	@Test
	void normalizesIntoAiGatewayExceptionWhenBothPrimaryAndFallbackFail() {
		when(modelProfileResolver.resolve("structured-reasoning"))
				.thenReturn(new ResolvedModel("primary", "primary-model", new ResolvedModel("fallback", "fallback-model", null)));
		AiProvider failingPrimary = stubProvider("primary", (request, model) -> {
			throw new IllegalStateException("primary blew up");
		});
		AiProvider failingFallback = stubProvider("fallback", (request, model) -> {
			throw new IllegalStateException("fallback blew up too");
		});
		AiGateway gateway = new AiGateway(modelProfileResolver, List.of(failingPrimary, failingFallback));

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
