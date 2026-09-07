package ai.architech.backend.core.ai;

/** Normalized failure from an AiProvider call - callers never need to know which provider-specific exception caused it. */
public class AiGatewayException extends RuntimeException {

	public AiGatewayException(String message, Throwable cause) {
		super(message, cause);
	}
}
