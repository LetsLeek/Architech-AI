package ai.architech.backend.core.ai;

/**
 * One concrete AI backend (e.g. a real Anthropic or OpenAI client, or - for now, since no
 * real provider is wired up yet - {@link MockAiProvider}). Never called directly by agent
 * code; only {@link AiGateway} knows providers exist at all.
 */
public interface AiProvider {

	/** Must match a {@code provider} value used in {@code architech.ai.model-profiles}. */
	String name();

	AiResponse invoke(AiRequest request, String model);
}
