package ai.architech.backend.core.qa.profiles;

import org.springframework.core.io.Resource;

public class InvalidQaProfileException extends RuntimeException {

	public InvalidQaProfileException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidQaProfileException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
