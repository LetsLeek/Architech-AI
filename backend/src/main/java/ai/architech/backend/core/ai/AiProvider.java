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

	/**
	 * Whether this provider actually has what it needs to be called (AIW-64) - {@code true} by
	 * default, since a provider like {@link MockAiProvider} has no credentials to be missing in
	 * the first place. A real provider overrides this to report whether its API key is present,
	 * so {@code ModelProfileCredentialsValidator} can fail backend startup clearly the moment a
	 * model profile is actually wired to a provider missing required credentials - never at
	 * first use, buried in whatever request happened to trigger it.
	 */
	default boolean isConfigured() {
		return true;
	}
}
