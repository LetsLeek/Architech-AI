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
 * Resolves the Website Developer Agent V1's {@code urn:aiw:schema:*:v1} schema family (AIW-133),
 * which - unlike the Requirements/Designer schemas {@link ArtifactSchemaValidator} validates -
 * genuinely composes by {@code $ref} across multiple documents (e.g.
 * {@code developer-execution-input.v1} references {@code urn:aiw:schema:customer-profile:v1},
 * {@code urn:aiw:schema:website-design-proposal:v1} and its own
 * {@code urn:aiw:schema:developer-common:v1} shared {@code $defs}).
 *
 * <p>Every document below is registered under its own real {@code $id} (never stripped, unlike
 * {@link ArtifactSchemaValidator}) into one shared {@link SchemaRegistry}, so cross-document
 * {@code $ref}s resolve without any network/remote fetch - every referenced URN is either one of
 * the Developer Agent's own frozen contracts or one of the pre-existing Requirements/Designer
 * schemas (given a URN {@code $id} by AIW-133 specifically so this composition is possible; their
 * bare-filename {@code $id}s before AIW-133 could not be {@code $ref}'d across files at all).
 *
 * <p>{@code urn:aiw:schema:website-design-proposal:v1} does not duplicate the Designer Agent's
 * single-proposal shape; it is a one-line {@code $ref} wrapper into
 * {@code website-design-proposal.v1.schema.json}, itself a two-key {@code $ref} onto
 * {@code design-proposal-set.schema.json}'s own {@code $defs/proposal} - see that file's
 * {@code $comment} for why a nested {@code $id} directly on {@code $defs/proposal} was rejected
 * (it would change the base URI used to resolve that subschema's own internal {@code #/$defs/...}
 * refs and could silently break the Designer Agent's existing M2 validation pipeline).
 *
 * <p>{@code urn:aiw:schema:developer-safe-integration-contract-view:v1} is the real, authoritative
 * Developer-safe contract projection schema (AIW-144) - see {@code core.integration}'s own
 * package documentation for how a persisted {@code IntegrationContract} row is resolved,
 * authorized and validated against exactly this schema before it is ever embedded into a
 * {@code developer-execution-input.v1} payload.
 */
@Component
public class DeveloperSchemaRegistry {

	private static final List<String> UPSTREAM_SCHEMA_LOCATIONS =
			List.of(
					"classpath:project-types/website/schemas/customer-profile.schema.json",
					"classpath:project-types/website/schemas/website-requirements.schema.json",
					"classpath:project-types/website/schemas/design-proposal-set.schema.json");

	private static final List<String> DEVELOPER_CONTRACT_LOCATIONS =
			List.of(
					"classpath:project-types/website/agents/developer-agent/contracts/common.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/website-design-proposal.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/developer-safe-integration-contract-view.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/developer-execution-input.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/developer-agent-result.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/implementation-anchor.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/functional-binding.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/unresolved-issue.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/developer-blocker.v1.schema.json",
					"classpath:project-types/website/agents/developer-agent/contracts/website-implementation-candidate.v1.schema.json");

	private final SchemaRegistry schemaRegistry;

	DeveloperSchemaRegistry(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
		Map<String, String> schemasById = new LinkedHashMap<>();
		for (String location : UPSTREAM_SCHEMA_LOCATIONS) {
			registerByOwnId(schemasById, resourceLoader, objectMapper, location);
		}
		for (String location : DEVELOPER_CONTRACT_LOCATIONS) {
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
	 * (e.g. {@code "urn:aiw:schema:developer-agent-result:v1"}), resolving every cross-document
	 * {@code $ref} against the schemas registered above. Detects and reports only - never repairs,
	 * normalizes or drops offending fields, matching {@link ArtifactSchemaValidator}'s contract.
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
