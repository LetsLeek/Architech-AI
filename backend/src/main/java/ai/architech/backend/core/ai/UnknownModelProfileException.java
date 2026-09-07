package ai.architech.backend.core.ai;

public class UnknownModelProfileException extends RuntimeException {

	public UnknownModelProfileException(String modelProfile) {
		super("No provider/model configured for model profile '" + modelProfile + "'");
	}
}
