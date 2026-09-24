package ai.architech.backend.core.documentation.locale;

import org.springframework.core.io.Resource;

public class InvalidLocaleRegistryException extends RuntimeException {

	public InvalidLocaleRegistryException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidLocaleRegistryException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
