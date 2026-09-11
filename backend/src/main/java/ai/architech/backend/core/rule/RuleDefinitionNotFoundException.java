package ai.architech.backend.core.rule;

public class RuleDefinitionNotFoundException extends RuntimeException {

	public RuleDefinitionNotFoundException(String id, int version) {
		super("No rule definition found for id '" + id + "' version " + version);
	}
}
