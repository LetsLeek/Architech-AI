package ai.architech.backend.core.documentation.errors;

import org.springframework.core.io.Resource;

public class InvalidDocumentationErrorRegistryException extends RuntimeException {

	public InvalidDocumentationErrorRegistryException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidDocumentationErrorRegistryException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
