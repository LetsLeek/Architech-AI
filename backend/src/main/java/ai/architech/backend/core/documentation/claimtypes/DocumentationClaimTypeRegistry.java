package ai.architech.backend.core.documentation.claimtypes;

import java.util.List;
import java.util.Optional;

/** The loaded {@code registries/claim-types.yaml} (AIW-196), 14 entries - one per {@code claimType} enum value. */
public record DocumentationClaimTypeRegistry(String registryVersion, List<DocumentationClaimType> claimTypes) {

	public Optional<DocumentationClaimType> byId(String id) {
		return claimTypes.stream().filter(c -> c.id().equals(id)).findFirst();
	}
}
