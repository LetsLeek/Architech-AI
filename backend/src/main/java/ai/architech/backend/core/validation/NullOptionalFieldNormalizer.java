package ai.architech.backend.core.validation;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Strips every object property whose value is a JSON {@code null} literal, at any nesting depth
 * (including inside arrays), before re-serializing. No schema in this codebase (project-types/
 * website/schemas/*.json) ever uses {@code "null"} as a meaningful JSON Schema type, so a model
 * emitting an explicit {@code null} for a field it means to omit (e.g. {@code "customKind": null}
 * on a section whose {@code kind} is not {@code "custom"}) is a harmless JSON-encoding quirk, not
 * a distinct semantic answer - stripping it before schema validation normalizes that encoding
 * convention, it does not repair or reinterpret content.
 *
 * <p>Deliberately separate from {@link ArtifactSchemaValidator}, whose own class javadoc requires
 * it to detect and report only, never repair/normalize/drop fields from the candidate on its own.
 * Callers that need this normalization compose it themselves against a copy of the candidate JSON
 * used only for the schema-conformance check - see {@code DesignerAgentRunner}.
 *
 * <p>A property whose value is merely an empty string, an empty object, or an empty array is left
 * untouched - only a literal JSON {@code null} value is stripped.
 */
@Component
public class NullOptionalFieldNormalizer {

	private final ObjectMapper objectMapper;

	NullOptionalFieldNormalizer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String stripNullFields(String json) {
		JsonNode root = objectMapper.readTree(json);
		stripNulls(root);
		return objectMapper.writeValueAsString(root);
	}

	private void stripNulls(JsonNode node) {
		if (node instanceof ObjectNode objectNode) {
			objectNode.removeIf(JsonNode::isNull);
			objectNode.forEach(this::stripNulls);
		} else if (node instanceof ArrayNode arrayNode) {
			arrayNode.forEach(this::stripNulls);
		}
	}
}
