package ai.architech.backend.core.qa.invariants;

import org.springframework.core.io.Resource;

public class InvalidFindingTaxonomyException extends RuntimeException {

	public InvalidFindingTaxonomyException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidFindingTaxonomyException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
