package ai.architech.backend.core.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Resolves an {@link AgentDefinition} by id and version from any {@code agent.yaml} (plus
 * its sibling {@code AGENT.md} role/persona text) found under {@code project-types/} on the
 * classpath. Deliberately does not know about "website" or any other specific project type
 * - it just looks for the file matching the requested id/version, wherever it happens to
 * live.
 *
 * <p>Resolution is fail-fast: a missing or malformed definition is reported as an exception
 * from {@link #resolve} itself, before any caller can go on to invoke the agent.
 */
@Component
public class AgentDefinitionLoader {

	private static final String AGENT_DEFINITION_PATTERN = "classpath*:project-types/**/agent.yaml";
	private static final String ROLE_CONTENT_FILENAME = "AGENT.md";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	AgentDefinitionLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public AgentDefinition resolve(String id, int version) {
		for (Resource resource : findAgentDefinitionResources()) {
			AgentDefinition definition = parse(resource);
			if (definition.id().equals(id) && definition.version() == version) {
				return definition;
			}
		}
		throw new AgentDefinitionNotFoundException(id, version);
	}

	private Resource[] findAgentDefinitionResources() {
		try {
			return resourceResolver.getResources(AGENT_DEFINITION_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for agent definitions", e);
		}
	}

	private AgentDefinition parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidAgentDefinitionException(resource, "Failed to read agent definition", e);
		}

		String roleContent = readRoleContent(resource);

		try {
			return toDefinition(raw, roleContent);
		} catch (RuntimeException e) {
			throw new InvalidAgentDefinitionException(resource, "Malformed agent definition", e);
		}
	}

	private String readRoleContent(Resource resource) {
		try {
			Resource contentResource = resource.createRelative(ROLE_CONTENT_FILENAME);
			try (InputStream in = contentResource.getInputStream()) {
				return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new InvalidAgentDefinitionException(resource, "Missing or unreadable " + ROLE_CONTENT_FILENAME, e);
		}
	}

	@SuppressWarnings("unchecked")
	private static AgentDefinition toDefinition(Map<String, Object> raw, String roleContent) {
		Map<String, Object> limitsRaw = requireMap(raw, "limits");
		Map<String, Object> outputsRaw = requireMap(raw, "outputs");
		Object artifactsRaw = outputsRaw.get("artifacts");
		if (!(artifactsRaw instanceof List)) {
			throw new IllegalStateException("outputs.artifacts is required");
		}

		List<AgentArtifactOutput> artifacts = ((List<Map<String, Object>>) artifactsRaw)
				.stream()
				.map(a -> new AgentArtifactOutput(
						(String) requireField(a, "type"),
						(String) requireField(a, "schema"),
						(Boolean) a.getOrDefault("required", Boolean.FALSE)))
				.toList();

		return new AgentDefinition(
				(Integer) requireField(raw, "schemaVersion"),
				(String) requireField(raw, "id"),
				(String) requireField(raw, "name"),
				(Integer) requireField(raw, "version"),
				(String) raw.get("description"),
				(String) requireField(raw, "modelProfile"),
				new AgentLimits((Integer) requireField(limitsRaw, "maxOutputTokens")),
				(List<String>) raw.getOrDefault("skills", List.of()),
				(List<String>) raw.getOrDefault("rules", List.of()),
				new AgentOutputs(artifacts),
				roleContent);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> requireMap(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (!(value instanceof Map)) {
			throw new IllegalStateException("Missing required object field: " + field);
		}
		return (Map<String, Object>) value;
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
