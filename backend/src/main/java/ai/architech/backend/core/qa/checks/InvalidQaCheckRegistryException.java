package ai.architech.backend.core.qa.checks;

import org.springframework.core.io.Resource;

public class InvalidQaCheckRegistryException extends RuntimeException {

	public InvalidQaCheckRegistryException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidQaCheckRegistryException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
