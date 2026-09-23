package ai.architech.backend.core.documentation.errors;

import java.util.List;
import java.util.Optional;

/** The loaded {@code registries/documentation-error-registry.yaml} (AIW-188), 39 error codes. */
public record DocumentationErrorRegistry(String registryVersion, List<String> notes, List<DocumentationErrorCode> errors) {

	public Optional<DocumentationErrorCode> byCode(String code) {
		return errors.stream().filter(e -> e.code().equals(code)).findFirst();
	}
}
