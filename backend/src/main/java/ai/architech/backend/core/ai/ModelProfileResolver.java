package ai.architech.backend.core.ai;

import org.springframework.stereotype.Component;

/**
 * Resolves a logical model profile to a concrete provider+model via {@link AiProperties}.
 * Fails before invocation: an unconfigured profile throws {@link UnknownModelProfileException}
 * from {@link #resolve} itself, not later when something tries to actually call the model.
 */
@Component
public class ModelProfileResolver {

	private final AiProperties properties;

	ModelProfileResolver(AiProperties properties) {
		this.properties = properties;
	}

	public ResolvedModel resolve(String modelProfile) {
		AiProperties.ModelProfileConfig config = properties.modelProfiles().get(modelProfile);
		if (config == null) {
			throw new UnknownModelProfileException(modelProfile);
		}
		return new ResolvedModel(config.provider(), config.model());
	}
}
