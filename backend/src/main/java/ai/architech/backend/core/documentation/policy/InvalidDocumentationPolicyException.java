package ai.architech.backend.core.documentation.policy;

import org.springframework.core.io.Resource;

public class InvalidDocumentationPolicyException extends RuntimeException {

	public InvalidDocumentationPolicyException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidDocumentationPolicyException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
