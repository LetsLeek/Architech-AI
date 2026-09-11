package ai.architech.backend.core.agent;

public class AgentDefinitionNotFoundException extends RuntimeException {

	public AgentDefinitionNotFoundException(String id, int version) {
		super("No agent definition found for id '" + id + "' version " + version);
	}
}
