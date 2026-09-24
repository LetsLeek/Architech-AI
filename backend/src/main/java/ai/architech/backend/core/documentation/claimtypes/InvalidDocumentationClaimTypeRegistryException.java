package ai.architech.backend.core.documentation.claimtypes;

import org.springframework.core.io.Resource;

public class InvalidDocumentationClaimTypeRegistryException extends RuntimeException {

	public InvalidDocumentationClaimTypeRegistryException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidDocumentationClaimTypeRegistryException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
