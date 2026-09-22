package ai.architech.backend.core.developer.tooling;

import org.springframework.core.io.Resource;

/** Thrown when a {@code tool-capability-profile.v1.yaml} exists but is malformed, or violates its own required safety contract. */
public class InvalidDeveloperToolCapabilityProfileException extends RuntimeException {

	public InvalidDeveloperToolCapabilityProfileException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidDeveloperToolCapabilityProfileException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
