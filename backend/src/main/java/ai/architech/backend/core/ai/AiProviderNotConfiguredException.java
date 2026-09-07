package ai.architech.backend.core.ai;

/** Thrown when a model profile resolves to a provider name that has no matching {@link AiProvider} bean. */
public class AiProviderNotConfiguredException extends RuntimeException {

	public AiProviderNotConfiguredException(String provider) {
		super("No AiProvider registered for provider '" + provider + "'");
	}
}
