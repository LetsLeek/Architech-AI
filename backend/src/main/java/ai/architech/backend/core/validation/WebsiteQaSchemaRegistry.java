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
 * Resolves the Website QA Agent V1's {@code urn:aiw:schema:*:v1} schema family (AIW-167) -
 * exactly the same idiom {@link DeveloperSchemaRegistry} already establishes for the Developer
 * Agent's own schema family: every document registered under its own real {@code $id} (never
 * stripped, unlike {@link ArtifactSchemaValidator}) into one shared {@link SchemaRegistry}, so
 * cross-document {@code $ref}s resolve without any network/remote fetch.
 *
 * <p>Unlike the Developer family, the frozen QA package ships only one genuine cross-document
 * reference ({@code qa-result.schema.json}'s {@code domainResults} items {@code $ref}ing
 * {@code domain-result.schema.json}) and no references at all into the upstream M1/M2/M3 schema
 * families - QA evaluates an already-immutable {@code WebsiteImplementationCandidate} by opaque
 * reference ({@code productAuthority.*Ref} strings in {@code qa-execution-input.v1}), it never
 * re-validates the upstream Customer Profile/Website Requirements/Design Proposal documents
 * themselves. The frozen package shipped with bare-filename {@code $ref}s and, for one schema, a
 * URL-style (not {@code urn:aiw:schema:*:v1}) {@code $id} - both retrofitted onto real URNs when
 * these files were placed under {@code project-types/}, the same "cannot be {@code $ref}'d across
 * files at all before URN retrofit" problem {@link DeveloperSchemaRegistry}'s own javadoc
 * describes for AIW-133.
 */
@Component
public class WebsiteQaSchemaRegistry {

	private static final List<String> QA_CONTRACT_LOCATIONS =
			List.of(
					"classpath:project-types/website/agents/website-qa-agent/contracts/qa-execution-input.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/semantic-qa-review-output.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/candidate-finding.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/authority-issue.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/evaluation-issue.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/domain-result.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/policy-evaluation.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/qa-result.schema.json",
					"classpath:project-types/website/agents/website-qa-agent/contracts/remediation-assessment.schema.json");

	private final SchemaRegistry schemaRegistry;

	WebsiteQaSchemaRegistry(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
		Map<String, String> schemasById = new LinkedHashMap<>();
		for (String location : QA_CONTRACT_LOCATIONS) {
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
	 * (e.g. {@code "urn:aiw:schema:qa-result:v1"}), resolving every cross-document {@code $ref}
	 * against the schemas registered above. Detects and reports only - never repairs, normalizes
	 * or drops offending fields, matching {@link ArtifactSchemaValidator}/{@link
	 * DeveloperSchemaRegistry}'s own contract.
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
