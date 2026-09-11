package ai.architech.backend.core.skill;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Resolves a {@link SkillDefinition} by id and version from any {@code skill.yaml} found
 * under {@code project-types/} on the classpath, together with every module under its
 * sibling {@code modules/} directory, sorted by filename and read in full - never left as
 * bare file references. Modules are not separate agent executions; they are compositional
 * instruction content for one skill invocation.
 *
 * <p>Resolution is fail-fast: a missing skill, a malformed skill.yaml, or a skill with no
 * modules all throw from {@link #resolve} itself, before any caller can proceed to use it.
 */
@Component
public class SkillLoader {

	private static final String SKILL_DEFINITION_PATTERN = "classpath*:project-types/**/skill.yaml";
	private static final String MODULE_PATTERN = "classpath*:project-types/**/modules/*.md";
	private static final String SKILL_YAML_FILENAME = "skill.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	SkillLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public SkillDefinition resolve(String id, int version) {
		for (Resource resource : findResources(SKILL_DEFINITION_PATTERN)) {
			SkillDefinition definition = parse(resource);
			if (definition.id().equals(id) && definition.version() == version) {
				return definition;
			}
		}
		throw new SkillDefinitionNotFoundException(id, version);
	}

	private Resource[] findResources(String pattern) {
		try {
			return resourceResolver.getResources(pattern);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for skill resources", e);
		}
	}

	private SkillDefinition parse(Resource skillYaml) {
		Map<String, Object> raw;
		try (InputStream in = skillYaml.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidSkillDefinitionException(skillYaml, "Failed to read skill definition", e);
		}

		try {
			List<SkillModule> modules = loadModulesFor(skillYaml);
			if (modules.isEmpty()) {
				throw new IllegalStateException("Skill has no modules");
			}

			return new SkillDefinition(
					(Integer) requireField(raw, "schemaVersion"),
					(String) requireField(raw, "id"),
					(String) requireField(raw, "name"),
					(Integer) requireField(raw, "version"),
					(String) raw.get("description"),
					modules);
		} catch (RuntimeException e) {
			throw new InvalidSkillDefinitionException(skillYaml, "Malformed skill definition", e);
		}
	}

	/**
	 * Modules are matched to their skill by URI prefix (the module's URI starts with the
	 * skill.yaml's own directory), then sorted by full URI - equivalent to sorting by
	 * filename here since every module for a given skill shares the same "modules/"
	 * parent, and filenames use zero-padded numeric prefixes (01, 02, ... 13).
	 */
	private List<SkillModule> loadModulesFor(Resource skillYaml) {
		String skillDirectory = uriOf(skillYaml).replace(SKILL_YAML_FILENAME, "");

		return Arrays.stream(findResources(MODULE_PATTERN))
				.filter(module -> uriOf(module).startsWith(skillDirectory + "modules/"))
				.sorted(Comparator.comparing(SkillLoader::uriOf))
				.map(module -> new SkillModule(fileNameOf(uriOf(module)), readContent(module)))
				.toList();
	}

	private static String uriOf(Resource resource) {
		try {
			return resource.getURI().toString();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to resolve resource URI: " + resource, e);
		}
	}

	private static String fileNameOf(String uri) {
		return uri.substring(uri.lastIndexOf('/') + 1);
	}

	private static String readContent(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read module content: " + resource, e);
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
