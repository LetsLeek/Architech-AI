package ai.architech.backend.core.ai;

import org.springframework.stereotype.Component;

/**
 * Placeholder provider with no real AI behind it - registered so the platform is fully
 * runnable end-to-end before any real provider credentials exist. Replace by registering a
 * real {@link AiProvider} bean (e.g. for Anthropic or OpenAI) and pointing a model profile
 * at its {@link #name()} in {@code application.yml}; this class can then be removed or left
 * in place for tests.
 */
@Component
class MockAiProvider implements AiProvider {

	@Override
	public String name() {
		return "mock";
	}

	@Override
	public AiResponse invoke(AiRequest request, String model) {
		// No real usage to report - null, not zero, since this never actually ran a model.
		return new AiResponse("mock", model, "", request.correlationId(), null, null);
	}
}
