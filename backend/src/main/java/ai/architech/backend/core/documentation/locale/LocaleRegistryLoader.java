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
 * Loads the frozen {@code registries/locale-registry.yaml} into a {@link LocaleRegistry}
 * (AIW-188), mirroring {@code FindingTaxonomyLoader}'s singleton-classpath-resource idiom.
 */
@Component
public class LocaleRegistryLoader {

	private static final String LOCALE_REGISTRY_PATTERN = "classpath*:project-types/**/documentation-agent/registries/locale-registry.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	private volatile LocaleRegistry cached;

	LocaleRegistryLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public LocaleRegistry load() {
		LocaleRegistry loaded = cached;
		if (loaded != null) {
			return loaded;
		}
		Resource resource = findRegistryResource();
		loaded = parse(resource);
		cached = loaded;
		return loaded;
	}

	private Resource findRegistryResource() {
		Resource[] resources;
		try {
			resources = resourceResolver.getResources(LOCALE_REGISTRY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for the Documentation locale registry", e);
		}
		if (resources.length != 1) {
			throw new IllegalStateException("Expected exactly one Documentation locale registry on the classpath, found " + resources.length);
		}
		return resources[0];
	}

	@SuppressWarnings("unchecked")
	private LocaleRegistry parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidLocaleRegistryException(resource, "Failed to read the Documentation locale registry", e);
		}

		try {
			return new LocaleRegistry(
					(String) requireField(raw, "registryVersion"), (List<String>) requireField(raw, "activeLocales"), (String) requireField(raw, "note"));
		} catch (RuntimeException e) {
			throw new InvalidLocaleRegistryException(resource, "Malformed Documentation locale registry", e);
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
