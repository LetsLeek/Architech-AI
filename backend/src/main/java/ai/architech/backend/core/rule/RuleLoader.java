package ai.architech.backend.core.rule;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Resolves a {@link RuleDefinition} by id and version from any {@code rule.yaml} (plus its
 * sibling {@code RULE.md}) found under {@code project-types/} on the classpath. Distinct
 * from {@code SkillLoader}: a Rule is a standalone evidence-integrity constraint, not a
 * sequence of instructional modules - the two are loaded independently and never merged.
 *
 * <p>Resolution is fail-fast: a missing rule, a malformed rule.yaml, or a missing RULE.md
 * all throw from {@link #resolve} itself, before any caller can proceed to use the rule.
 */
@Component
public class RuleLoader {

	private static final String RULE_DEFINITION_PATTERN = "classpath*:project-types/**/rule.yaml";
	private static final String RULE_CONTENT_FILENAME = "RULE.md";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	RuleLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public RuleDefinition resolve(String id, int version) {
		for (Resource resource : findRuleDefinitionResources()) {
			RuleDefinition definition = parse(resource);
			if (definition.id().equals(id) && definition.version() == version) {
				return definition;
			}
		}
		throw new RuleDefinitionNotFoundException(id, version);
	}

	private Resource[] findRuleDefinitionResources() {
		try {
			return resourceResolver.getResources(RULE_DEFINITION_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for rule definitions", e);
		}
	}

	private RuleDefinition parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidRuleDefinitionException(resource, "Failed to read rule definition", e);
		}

		String content = readRuleContent(resource);

		try {
			return new RuleDefinition(
					(Integer) requireField(raw, "schemaVersion"),
					(String) requireField(raw, "id"),
					(String) requireField(raw, "name"),
					(Integer) requireField(raw, "version"),
					(String) raw.get("description"),
					content);
		} catch (RuntimeException e) {
			throw new InvalidRuleDefinitionException(resource, "Malformed rule definition", e);
		}
	}

	private String readRuleContent(Resource resource) {
		try {
			Resource contentResource = resource.createRelative(RULE_CONTENT_FILENAME);
			try (InputStream in = contentResource.getInputStream()) {
				return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new InvalidRuleDefinitionException(resource, "Missing or unreadable " + RULE_CONTENT_FILENAME, e);
		}
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
