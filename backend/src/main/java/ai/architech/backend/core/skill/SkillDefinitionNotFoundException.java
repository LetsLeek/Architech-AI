package ai.architech.backend.core.skill;

public class SkillDefinitionNotFoundException extends RuntimeException {

	public SkillDefinitionNotFoundException(String id, int version) {
		super("No skill definition found for id '" + id + "' version " + version);
	}
}
