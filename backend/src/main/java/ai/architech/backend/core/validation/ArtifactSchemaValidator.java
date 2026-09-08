package ai.architech.backend.core.validation;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.Resource;
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
 * <p>Both frozen schema files declare a bare filename as {@code $id} (e.g.
 * {@code "customer-profile.schema.json"}), which this validator library rejects as "not a
 * valid $id" since it isn't a URI. The {@code $id} is only the schema's own
 * self-identification for {@code $ref} resolution - both schemas only use local,
 * fragment-only {@code $ref}s (e.g. {@code #/$defs/business}), which resolve against the
 * document regardless of its {@code $id}. Stripping it in memory before registering the
 * schema changes zero validation constraints; the frozen file on disk is never touched.
 */
@Component
public class ArtifactSchemaValidator {

	private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
	private final ObjectMapper objectMapper;

	ArtifactSchemaValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public SchemaValidationResult validate(Resource schemaResource, String candidateJson) {
		Schema schema = loadSchema(schemaResource);

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

	private Schema loadSchema(Resource schemaResource) {
		try (InputStream in = schemaResource.getInputStream()) {
			JsonNode schemaNode = objectMapper.readTree(in);
			if (schemaNode instanceof ObjectNode objectNode) {
				objectNode.remove("$id");
			}
			return schemaRegistry.getSchema(schemaNode);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to load schema " + schemaResource, e);
		}
	}
}
