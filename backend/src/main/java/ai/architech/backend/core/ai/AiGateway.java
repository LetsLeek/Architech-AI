package ai.architech.backend.core.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only way agent runtime code calls AI - never a provider SDK directly. Resolves the
 * request's logical model profile to a concrete provider+model, dispatches to the matching
 * {@link AiProvider}, and normalizes any failure into {@link AiGatewayException}.
 *
 * <p>Falls back to {@link ResolvedModel#fallback()} if the primary provider throws (AIW-66) -
 * only when a model profile explicitly names one (see {@link AiProperties.ModelProfileConfig}),
 * never automatically inferred. This happens entirely within one {@link #invoke} call: from
 * {@code BoundedRetryAgentRunner}'s perspective nothing changes about the existing bounded
 * retry budget (AIW-38) - one attempt may now try up to as many providers as the configured
 * fallback chain is deep before that attempt counts as failed, but the number of attempts
 * itself is untouched. Whichever provider actually answers returns its own {@link AiResponse}
 * unmodified, so the real provider/model that served the request is never hidden - {@link
 * AiResponse#provider()} always names the provider that actually responded, not the profile's
 * configured primary.
 */
@Component
public class AiGateway {

	private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

	private final ModelProfileResolver modelProfileResolver;
	private final Map<String, AiProvider> providersByName;

	AiGateway(ModelProfileResolver modelProfileResolver, List<AiProvider> providers) {
		this.modelProfileResolver = modelProfileResolver;
		this.providersByName = providers.stream().collect(Collectors.toMap(AiProvider::name, Function.identity()));
	}

	public AiResponse invoke(AiRequest request) {
		return invoke(request, modelProfileResolver.resolve(request.modelProfile()));
	}

	private AiResponse invoke(AiRequest request, ResolvedModel resolved) {
		AiProvider provider = providersByName.get(resolved.provider());
		if (provider == null) {
			throw new AiProviderNotConfiguredException(resolved.provider());
		}

		try {
			return provider.invoke(request, resolved.model());
		} catch (RuntimeException e) {
			if (resolved.fallback() != null) {
				log.warn(
						"AI provider '{}' failed for model profile '{}', falling back to '{}'",
						resolved.provider(),
						request.modelProfile(),
						resolved.fallback().provider(),
						e);
				return invoke(request, resolved.fallback());
			}
			throw new AiGatewayException("AI provider '" + resolved.provider() + "' failed", e);
		}
	}
}
