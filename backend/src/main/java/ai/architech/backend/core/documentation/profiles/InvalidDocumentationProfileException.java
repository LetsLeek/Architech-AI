package ai.architech.backend.core.documentation.profiles;

import org.springframework.core.io.Resource;

public class InvalidDocumentationProfileException extends RuntimeException {

	public InvalidDocumentationProfileException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidDocumentationProfileException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
