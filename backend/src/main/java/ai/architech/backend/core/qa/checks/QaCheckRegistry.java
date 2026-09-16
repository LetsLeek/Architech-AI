package ai.architech.backend.core.qa.checks;

import java.util.List;
import java.util.Optional;

/**
 * The loaded, versioned {@code website-qa-check-registry@1.0.0} (AIW-172) -
 * {@code QAExecutionPreflightValidator.EXPECTED_CHECK_REGISTRY_REF} already checks a
 * {@code QaExecution}'s own claimed ref against this exact string.
 */
public record QaCheckRegistry(String ref, List<QaCheckRegistryEntry> checks) {

	/** Empty when {@code code} is not a known registry entry - AIW-172's own "unknown check codes are rejected". */
	public Optional<QaCheckRegistryEntry> resolve(String code) {
		return checks.stream().filter(entry -> entry.code().equals(code)).findFirst();
	}
}
