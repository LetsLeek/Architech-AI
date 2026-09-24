package ai.architech.backend.core.rule;

import org.springframework.core.io.Resource;

/** Thrown when a rule.yaml exists but is missing required fields, fails to parse, or its RULE.md content is missing. */
public class InvalidRuleDefinitionException extends RuntimeException {

	public InvalidRuleDefinitionException(Resource resource, String message, Throwable cause) {
		super(message + " (" + resource + ")", cause);
	}
}
