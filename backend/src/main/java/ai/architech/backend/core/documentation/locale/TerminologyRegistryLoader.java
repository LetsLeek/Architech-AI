package ai.architech.backend.core.documentation.locale;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads {@code de-AT} and {@code en-GB} from {@code project-types/**}{@code
 * /documentation-agent/registries/terminology/*.yaml} on the classpath (AIW-188), mirroring
 * {@code DocumentationProfileLoader}'s own "scan broadly, resolve by exact key" idiom.
 */
@Component
public class TerminologyRegistryLoader {

	private static final String TERMINOLOGY_PATTERN =
			"classpath*:project-types/**/documentation-agent/registries/terminology/*.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	TerminologyRegistryLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public TerminologyRegistry resolve(String locale) {
		for (Resource resource : findTerminologyResources()) {
			TerminologyRegistry registry = toRegistry(readRaw(resource), resource);
			if (registry.locale().equals(locale)) {
				return registry;
			}
		}
		throw new TerminologyRegistryNotFoundException(locale);
	}

	private Resource[] findTerminologyResources() {
		try {
			return resourceResolver.getResources(TERMINOLOGY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for Documentation terminology registries", e);
		}
	}

	private Map<String, Object> readRaw(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return yaml.load(in);
		} catch (IOException e) {
			throw new InvalidLocaleRegistryException(resource, "Failed to read Documentation terminology registry", e);
		}
	}

	@SuppressWarnings("unchecked")
	private TerminologyRegistry toRegistry(Map<String, Object> raw, Resource resource) {
		try {
			return new TerminologyRegistry(
					(String) requireField(raw, "locale"),
					(List<String>) requireField(raw, "protectedEnums"),
					(Map<String, String>) requireField(raw, "sectionTitles"),
					(Map<String, String>) requireField(raw, "requirements"),
					(String) requireField(raw, "legalNames"));
		} catch (RuntimeException e) {
			throw new InvalidLocaleRegistryException(resource, "Malformed Documentation terminology registry", e);
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
