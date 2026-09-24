package ai.architech.backend.core.validation;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates one candidate's JSON content against one of the frozen JSON Schemas
 * (customer-profile.schema.json / website-requirements.schema.json), draft 2020-12 - the
 * dialect both schema files declare. Detects and reports only: never repairs, normalizes,
 * or drops offending fields from the candidate on its own.
 *
 * <p>{@code schemaJson} is the schema's raw text - callers get this from
 * {@code AgentArtifactOutput.schemaContent()} (resolved once by {@code AgentDefinitionLoader}
 * relative to agent.yaml, the same content also sent to the model as part of the prompt -
 * AIW-127), not a classpath {@code Resource} this class would have to load itself.
 *
 * <p>Both frozen schema files declare their own {@code $id} (a {@code urn:aiw:schema:*:v1}
 * value since AIW-133, so the Developer Agent's schemas can {@code $ref} them by that URN) but
 * this validator only ever validates one self-contained document at a time and never needs
 * cross-document resolution - both schemas only use local, fragment-only {@code $ref}s (e.g.
 * {@code #/$defs/business}), which resolve against the document regardless of its {@code $id}.
 * Stripping {@code $id} in memory before registering the schema changes zero validation
 * constraints; the frozen file on disk is never touched. (Cross-document {@code urn:aiw:schema:*}
 * resolution for the Developer Agent's own schemas is handled separately by
 * {@link DeveloperSchemaRegistry}, which registers documents under their real {@code $id} rather
 * than stripping it - the two validators serve different schema shapes and must not be merged.)
 */
@Component
public class ArtifactSchemaValidator {

	private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
	private final ObjectMapper objectMapper;

	ArtifactSchemaValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public SchemaValidationResult validate(String schemaJson, String candidateJson) {
		Schema schema = loadSchema(schemaJson);

		List<Error> errors;
		try {
			errors = schema.validate(candidateJson, InputFormat.JSON);
		} catch (RuntimeException e) {
			return new SchemaValidationResult(
					false, List.of(new SchemaValidationIssue("$", "candidate is not valid JSON: " + e.getMessage())));
		}

		if (errors.isEmpty()) {
			return SchemaValidationResult.passed();
		}

		return new SchemaValidationResult(
				false,
				errors.stream()
						.map(error -> new SchemaValidationIssue(error.getInstanceLocation().toString(), error.getMessage()))
						.toList());
	}

	private Schema loadSchema(String schemaJson) {
		JsonNode schemaNode = objectMapper.readTree(schemaJson);
		if (schemaNode instanceof ObjectNode objectNode) {
			objectNode.remove("$id");
		}
		return schemaRegistry.getSchema(schemaNode);
	}
}
