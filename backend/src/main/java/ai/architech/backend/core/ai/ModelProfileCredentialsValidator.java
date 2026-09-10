package ai.architech.backend.core.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Fails backend startup clearly the moment a configured {@code architech.ai.model-profiles}
 * entry is wired to a provider that {@link AiProvider#isConfigured()} reports as missing
 * required credentials (AIW-64) - consistent with this platform's existing config-failure
 * behavior elsewhere (a missing datasource property already fails startup the same way, see
 * AIW-18/20). Runs as a constructor check at bean-creation time, so a bad configuration never
 * reaches "the app looks up and running, but the first real request fails" - the previous
 * behavior, since every {@link AiProvider} implementation deliberately defers its own
 * credential check to first invocation (see {@link AnthropicProvider}/{@link OpenAiProvider}'s
 * own javadoc) precisely so the platform stays runnable on {@link MockAiProvider} regardless
 * of whether real credentials exist.
 *
 * <p>Only checks profiles that resolve to a provider bean that actually exists - an unknown
 * provider name is {@link ModelProfileResolver}'s concern (thrown at resolve time, not startup,
 * since a model profile can legitimately be declared before its provider bean exists yet), not
 * this validator's.
 *
 * <p>Also walks a profile's {@link AiProperties.ModelProfileConfig#fallback()} chain, if any
 * (AIW-66) - a fallback is "wired to" its profile exactly as much as the primary provider is,
 * so it gets the same fail-fast treatment rather than only surfacing a missing key the first
 * time the primary actually fails and something tries to fall back to it.
 */
@Component
class ModelProfileCredentialsValidator {

	ModelProfileCredentialsValidator(AiProperties properties, List<AiProvider> providers) {
		Map<String, AiProvider> providersByName =
				providers.stream().collect(Collectors.toMap(AiProvider::name, Function.identity()));

		properties.modelProfiles().forEach((profileName, config) -> validateChain(profileName, config, providersByName));
	}

	private static void validateChain(
			String profileName, AiProperties.ModelProfileConfig config, Map<String, AiProvider> providersByName) {
		AiProvider provider = providersByName.get(config.provider());
		if (provider != null && !provider.isConfigured()) {
			throw new IllegalStateException(
					"Model profile '" + profileName + "' is wired to provider '" + config.provider()
							+ "', but that provider is missing required credentials. Set its API key "
							+ "(see .env.example) before starting with this configuration.");
		}
		if (config.fallback() != null) {
			validateChain(profileName, config.fallback(), providersByName);
		}
	}
}
