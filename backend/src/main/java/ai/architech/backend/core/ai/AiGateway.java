package ai.architech.backend.core.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The only way agent runtime code calls AI - never a provider SDK directly. Resolves the
 * request's logical model profile to a concrete provider+model, dispatches to the matching
 * {@link AiProvider}, and normalizes any failure into {@link AiGatewayException}.
 */
@Component
public class AiGateway {

	private final ModelProfileResolver modelProfileResolver;
	private final Map<String, AiProvider> providersByName;

	AiGateway(ModelProfileResolver modelProfileResolver, List<AiProvider> providers) {
		this.modelProfileResolver = modelProfileResolver;
		this.providersByName = providers.stream().collect(Collectors.toMap(AiProvider::name, Function.identity()));
	}

	public AiResponse invoke(AiRequest request) {
		ResolvedModel resolved = modelProfileResolver.resolve(request.modelProfile());

		AiProvider provider = providersByName.get(resolved.provider());
		if (provider == null) {
			throw new AiProviderNotConfiguredException(resolved.provider());
		}

		try {
			return provider.invoke(request, resolved.model());
		} catch (RuntimeException e) {
			throw new AiGatewayException("AI provider '" + resolved.provider() + "' failed", e);
		}
	}
}
