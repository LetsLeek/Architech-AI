package ai.architech.backend.core.validation;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves the Website Documentation Agent V1's {@code urn:aiw:schema:documentation:*:v1} schema
 * family (AIW-187) - the same idiom {@link WebsiteQaSchemaRegistry} already establishes for the
 * Website QA Agent's own schema family: every document registered under its own real {@code $id}
 * (never stripped, unlike {@link ArtifactSchemaValidator}) into one shared {@link SchemaRegistry},
 * so cross-document {@code $ref}s resolve without any network/remote fetch.
 *
 * <p>The frozen Documentation package ships 34 schema files across nine source categories
 * (agent-output, common, config, input, package, rendering, reports, validation, workflow), each
 * originally identified by a URL-style (not {@code urn:aiw:schema:*:v1}) {@code $id} and
 * cross-referenced via bare-filename or relative-path {@code $ref}s - both retrofitted onto real
 * URNs when these files were placed under {@code project-types/}. Unlike the raw package's own
 * category subdirectories, the retrofitted copies under {@code contracts/} are kept flat: every
 * source filename is unique across categories, flat matches {@link WebsiteQaSchemaRegistry}'s own
 * precedent most directly, and it needs no subdirectory-traversal logic here - only a flat {@link
 * List} of classpath locations.
 */
@Component
public class DocumentationSchemaRegistry {

	private static final String CONTRACTS_ROOT =
			"classpath:project-types/website/agents/documentation-agent/contracts/";

	private static final List<String> DOCUMENTATION_CONTRACT_LOCATIONS =
			List.of(
					CONTRACTS_ROOT + "documentation-claim-candidate.schema.json",
					CONTRACTS_ROOT + "documentation-section-candidate.schema.json",
					CONTRACTS_ROOT + "semantic-block-candidate.schema.json",
					CONTRACTS_ROOT + "semantic-documentation-candidate.schema.json",
					CONTRACTS_ROOT + "semantic-document-candidate.schema.json",
					CONTRACTS_ROOT + "artifact-ref.schema.json",
					CONTRACTS_ROOT + "authority-catalog-entry.schema.json",
					CONTRACTS_ROOT + "authority-ref.schema.json",
					CONTRACTS_ROOT + "safe-value.schema.json",
					CONTRACTS_ROOT + "documentation-policy.schema.json",
					CONTRACTS_ROOT + "documentation-profile.schema.json",
					CONTRACTS_ROOT + "documentation-workflow-policy.schema.json",
					CONTRACTS_ROOT + "context-issue.schema.json",
					CONTRACTS_ROOT + "context-state.schema.json",
					CONTRACTS_ROOT + "documentation-context.schema.json",
					CONTRACTS_ROOT + "finding-disclosure-view.schema.json",
					CONTRACTS_ROOT + "safe-fact.schema.json",
					CONTRACTS_ROOT + "canonical-block.schema.json",
					CONTRACTS_ROOT + "canonical-section.schema.json",
					CONTRACTS_ROOT + "documentation-artifact-version.schema.json",
					CONTRACTS_ROOT + "documentation-claim.schema.json",
					CONTRACTS_ROOT + "documentation-package-candidate.schema.json",
					CONTRACTS_ROOT + "documentation-package-version.schema.json",
					CONTRACTS_ROOT + "documentation-render.schema.json",
					CONTRACTS_ROOT + "artifact-version-manifest.schema.json",
					CONTRACTS_ROOT + "deterministic-report.schema.json",
					CONTRACTS_ROOT + "functional-binding-report.schema.json",
					CONTRACTS_ROOT + "implementation-manifest.schema.json",
					CONTRACTS_ROOT + "integration-reference-report.schema.json",
					CONTRACTS_ROOT + "qa-finding-register.schema.json",
					CONTRACTS_ROOT + "documentation-validation-issue.schema.json",
					CONTRACTS_ROOT + "documentation-validation-result.schema.json",
					CONTRACTS_ROOT + "documentation-request.schema.json",
					CONTRACTS_ROOT + "documentation-run.schema.json");

	private final SchemaRegistry schemaRegistry;

	DocumentationSchemaRegistry(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
		Map<String, String> schemasById = new LinkedHashMap<>();
		for (String location : DOCUMENTATION_CONTRACT_LOCATIONS) {
			registerByOwnId(schemasById, resourceLoader, objectMapper, location);
		}

		this.schemaRegistry =
				SchemaRegistry.builder()
						.defaultDialectId(SpecificationVersion.DRAFT_2020_12.getDialectId())
						.schemas(schemasById)
						.build();
	}

	/**
	 * Validates {@code candidateJson} against the frozen schema identified by {@code schemaUrn}
	 * (e.g. {@code "urn:aiw:schema:documentation:documentation-context:v1"}), resolving every
	 * cross-document {@code $ref} against the schemas registered above. Detects and reports only -
	 * never repairs, normalizes or drops offending fields, matching {@link
	 * ArtifactSchemaValidator}/{@link WebsiteQaSchemaRegistry}'s own contract.
	 */
	public SchemaValidationResult validate(String schemaUrn, String candidateJson) {
		Schema schema = schemaRegistry.getSchema(SchemaLocation.of(schemaUrn));

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

	private void registerByOwnId(
			Map<String, String> schemasById, ResourceLoader resourceLoader, ObjectMapper objectMapper, String location) {
		String content = readContent(resourceLoader, location);
		JsonNode schemaNode = objectMapper.readTree(content);
		JsonNode idNode = schemaNode.get("$id");
		if (idNode == null || !idNode.isString()) {
			throw new IllegalStateException("Schema at " + location + " has no string \"$id\" to register it under");
		}
		schemasById.put(idNode.asString(), content);
	}

	private String readContent(ResourceLoader resourceLoader, String location) {
		Resource resource = resourceLoader.getResource(location);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read schema resource " + location, e);
		}
	}
}
