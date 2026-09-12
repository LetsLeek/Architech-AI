package ai.architech.backend.core.validation;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Walks an entire candidate JSON tree and collects every string cited as a website-requirements
 * canonical reference, at any nesting depth. The design-proposal-set schema uses two distinct
 * field shapes for the same reference domain: {@code requirementRefs} (an array, used almost
 * everywhere a design decision traces back to a requirement) and the singular
 * {@code requirementRef} (used only inside a {@code navigationTarget} of type "requirement").
 * Mirrors {@link SourceRefExtractor}'s generic-walk approach so callers don't need to hard-code
 * every requirementRefs/requirementRef-bearing shape in the frozen schema.
 */
final class RequirementRefExtractor {

	private RequirementRefExtractor() {}

	static Set<String> extract(JsonNode root) {
		Set<String> refs = new HashSet<>();
		collect(root, refs);
		return refs;
	}

	private static void collect(JsonNode node, Set<String> refs) {
		if (node.isObject()) {
			JsonNode requirementRefs = node.path("requirementRefs");
			if (requirementRefs.isArray()) {
				for (JsonNode ref : requirementRefs) {
					if (ref.isTextual()) {
						refs.add(ref.asString());
					}
				}
			}
			JsonNode requirementRef = node.path("requirementRef");
			if (requirementRef.isTextual()) {
				refs.add(requirementRef.asString());
			}
			for (String property : node.propertyNames()) {
				collect(node.path(property), refs);
			}
		} else if (node.isArray()) {
			for (JsonNode item : node) {
				collect(item, refs);
			}
		}
	}
}
