package ai.architech.backend.core.qa.tooling;

import org.springframework.core.io.Resource;

/** Thrown when a {@code tool-capability-profile.v1.yaml} exists but is malformed, or violates its own required safety contract. */
public class InvalidQaToolCapabilityProfileException extends RuntimeException {

	public InvalidQaToolCapabilityProfileException(Resource resource, String message) {
		super(message + " (" + resource + ")");
	}

	public InvalidQaToolCapabilityProfileException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
