package ai.architech.backend.core.validation;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Walks an entire candidate JSON tree and collects every string found under a property
 * literally named {@code sourceRefs}, at any nesting depth. Both frozen schemas scatter
 * {@code sourceRefs} across many different nested object shapes (socialLink, providedClaim,
 * unknown, conflictStatement, provenance, goal, audience, contentRequirement, ...) - walking
 * generically avoids hard-coding every one of those shapes here and stays correct if the
 * schemas grow new sourceRefs-bearing objects later.
 */
final class SourceRefExtractor {

	private SourceRefExtractor() {}

	static Set<String> extract(JsonNode root) {
		Set<String> refs = new HashSet<>();
		collect(root, refs);
		return refs;
	}

	private static void collect(JsonNode node, Set<String> refs) {
		if (node.isObject()) {
			JsonNode sourceRefs = node.path("sourceRefs");
			if (sourceRefs.isArray()) {
				for (JsonNode ref : sourceRefs) {
					if (ref.isTextual()) {
						refs.add(ref.asString());
					}
				}
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
