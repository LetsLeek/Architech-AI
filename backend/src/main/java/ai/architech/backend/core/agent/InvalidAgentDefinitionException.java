package ai.architech.backend.core.agent;

import org.springframework.core.io.Resource;

/** Thrown when an agent.yaml exists but is missing required fields or fails to parse. */
public class InvalidAgentDefinitionException extends RuntimeException {

	public InvalidAgentDefinitionException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
