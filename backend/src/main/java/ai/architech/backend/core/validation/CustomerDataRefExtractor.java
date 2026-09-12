package ai.architech.backend.core.validation;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Walks an entire candidate JSON tree and collects every string cited as a customer-profile
 * canonical reference, at any nesting depth. Mirrors {@link RequirementRefExtractor}: the
 * design-proposal-set schema uses both the array field {@code customerDataRefs} (on sections
 * and elements) and the singular {@code customerDataRef} (inside a {@code navigationTarget} of
 * type "customer-data").
 */
final class CustomerDataRefExtractor {

	private CustomerDataRefExtractor() {}

	static Set<String> extract(JsonNode root) {
		Set<String> refs = new HashSet<>();
		collect(root, refs);
		return refs;
	}

	private static void collect(JsonNode node, Set<String> refs) {
		if (node.isObject()) {
			JsonNode customerDataRefs = node.path("customerDataRefs");
			if (customerDataRefs.isArray()) {
				for (JsonNode ref : customerDataRefs) {
					if (ref.isTextual()) {
						refs.add(ref.asString());
					}
				}
			}
			JsonNode customerDataRef = node.path("customerDataRef");
			if (customerDataRef.isTextual()) {
				refs.add(customerDataRef.asString());
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
