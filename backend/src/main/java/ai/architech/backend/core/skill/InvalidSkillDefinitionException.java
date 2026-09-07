package ai.architech.backend.core.skill;

import org.springframework.core.io.Resource;

/** Thrown when a skill.yaml exists but is malformed, missing required fields, or has no modules. */
public class InvalidSkillDefinitionException extends RuntimeException {

	public InvalidSkillDefinitionException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
