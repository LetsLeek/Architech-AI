package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Walks an entire candidate JSON tree and collects every string found under a property
 * literally named {@code localRef}, at any nesting depth - duplicates included, since finding
 * duplicates is the whole point of {@link LocalRefUniquenessValidator}. Mirrors
 * {@link SourceRefExtractor}'s generic-walk approach so callers don't need to hard-code every
 * localRef-bearing shape in either frozen schema.
 */
final class LocalRefExtractor {

	private LocalRefExtractor() {}

	static List<String> extract(JsonNode root) {
		List<String> refs = new ArrayList<>();
		collect(root, refs);
		return refs;
	}

	private static void collect(JsonNode node, List<String> refs) {
		if (node.isObject()) {
			JsonNode localRef = node.path("localRef");
			if (localRef.isTextual()) {
				refs.add(localRef.asString());
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
