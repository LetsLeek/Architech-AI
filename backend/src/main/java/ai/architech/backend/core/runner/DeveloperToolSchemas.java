package ai.architech.backend.core.runner;

import ai.architech.backend.core.ai.ToolSchema;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The exact three tools advertised to the model for one Developer tool-calling turn (AIW-184) -
 * {@code filesystem}, {@code project_execution}, {@code git_inspect}. Deliberately includes every
 * git subcommand a model might plausibly attempt (not only the three allowed ones) in the {@code
 * command} enum, rather than omitting the prohibited ones from the schema itself: the actual
 * enforcement is {@link DeveloperToolDispatcher}'s allowlist check against a real {@link
 * ai.architech.backend.core.developer.tooling.DeveloperToolCapabilityProfile}, never "the model
 * was never told the option existed" - the AC's own denied-tool scenario (e.g. {@code git push})
 * would be untestable if the schema itself hid the option.
 */
final class DeveloperToolSchemas {

	private DeveloperToolSchemas() {}

	static List<ToolSchema> all(ObjectMapper objectMapper) {
		return List.of(filesystem(objectMapper), projectExecution(objectMapper), gitInspect(objectMapper));
	}

	private static ToolSchema filesystem(ObjectMapper objectMapper) {
		ObjectNode schema = objectMapper.createObjectNode();
		schema.put("type", "object");
		ObjectNode properties = schema.putObject("properties");
		stringEnumProperty(properties, "operation", List.of("list", "read", "search", "write", "patch", "mkdir", "move", "delete"));
		stringProperty(properties, "path");
		stringProperty(properties, "content");
		stringProperty(properties, "oldContent");
		stringProperty(properties, "newContent");
		stringProperty(properties, "pattern");
		stringProperty(properties, "destination");
		requireProperties(schema, "operation", "path");
		return new ToolSchema(
				"filesystem",
				"Read, write, or otherwise inspect one file/directory inside the isolated workspace. "
						+ "Every path is relative to the workspace root.",
				schema);
	}

	private static ToolSchema projectExecution(ObjectMapper objectMapper) {
		ObjectNode schema = objectMapper.createObjectNode();
		schema.put("type", "object");
		ObjectNode properties = schema.putObject("properties");
		stringEnumProperty(properties, "task", List.of("install", "typecheck", "lint", "test", "build"));
		requireProperties(schema, "task");
		return new ToolSchema(
				"project_execution",
				"Run one bounded, pre-approved project task inside the workspace and get its exit code, "
						+ "stdout and stderr back.",
				schema);
	}

	private static ToolSchema gitInspect(ObjectMapper objectMapper) {
		ObjectNode schema = objectMapper.createObjectNode();
		schema.put("type", "object");
		ObjectNode properties = schema.putObject("properties");
		stringEnumProperty(
				properties,
				"command",
				List.of("status", "diff", "diff-stat", "branch", "checkout", "merge", "rebase", "commit", "push", "force-push"));
		requireProperties(schema, "command");
		return new ToolSchema(
				"git_inspect",
				"Inspect git state read-only (status/diff/diff-stat only). Any mutating git operation is "
						+ "refused and recorded, never executed.",
				schema);
	}

	private static void stringProperty(ObjectNode properties, String name) {
		properties.putObject(name).put("type", "string");
	}

	private static void stringEnumProperty(ObjectNode properties, String name, List<String> values) {
		ObjectNode property = properties.putObject(name);
		property.put("type", "string");
		ArrayNode enumNode = property.putArray("enum");
		values.forEach(enumNode::add);
	}

	private static void requireProperties(ObjectNode schema, String... names) {
		ArrayNode required = schema.putArray("required");
		for (String name : names) {
			required.add(name);
		}
	}
}
