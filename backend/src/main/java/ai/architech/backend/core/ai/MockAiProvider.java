package ai.architech.backend.core.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Placeholder provider with no real AI behind it - registered so the platform is fully
 * runnable end-to-end before any real provider credentials exist. Replace by registering a
 * real {@link AiProvider} bean (e.g. for Anthropic or OpenAI) and pointing a model profile
 * at its {@link #name()} in {@code application.yml}; this class can then be removed or left
 * in place for tests.
 *
 * <p>{@link #script} lets a test drive a deterministic sequence of canned responses - including
 * {@code tool_use} turns - with zero real Anthropic calls (AIW-184). Scripted responses are
 * consumed strictly in order, one per {@link #invoke} call, regardless of {@code correlationId}:
 * a caller like the new multi-turn tool-calling loop generates its own {@code AgentExecution}
 * (and therefore its own correlation id) internally, so no test could know that id ahead of the
 * call it's scripting for. Once the script is exhausted, calls fall back to the original
 * always-empty response, so every pre-existing test that never calls {@link #script} keeps
 * seeing exactly today's behavior.
 *
 * <p>Because scripts are shared, unkeyed state on a singleton Spring bean, a test that scripts
 * anything must clear it afterward via {@link #resetScripts()} (e.g. in {@code @AfterEach}) so
 * nothing leaks into a later test reusing the same cached Spring context. Public (not
 * package-private, unlike before this ticket) specifically so tests outside {@code core.ai} can
 * script it directly.
 */
@Component
public class MockAiProvider implements AiProvider {

	private final Deque<AiResponse> script = new ArrayDeque<>();

	@Override
	public String name() {
		return "mock";
	}

	@Override
	public synchronized AiResponse invoke(AiRequest request, String model) {
		AiResponse scripted = script.pollFirst();
		if (scripted != null) {
			return scripted;
		}
		// No real usage to report - null, not zero, since this never actually ran a model.
		return new AiResponse("mock", model, "", request.correlationId(), null, null, null, null);
	}

	/** Appends a fixed, in-order sequence of responses - each {@link #invoke} call consumes exactly one, until the sequence is exhausted. */
	public synchronized void script(List<AiResponse> responses) {
		script.addAll(responses);
	}

	/** Clears every scripted response - call from a test's own cleanup so nothing leaks into a later test reusing this bean. */
	public synchronized void resetScripts() {
		script.clear();
	}
}
