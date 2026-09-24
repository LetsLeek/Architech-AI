package ai.architech.backend.core.qa.checks;

import ai.architech.backend.core.qa.tooling.QaToolCapability;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads the frozen {@code registries/qa-checks.yaml} into a {@link QaCheckRegistry} (AIW-172),
 * mirroring {@code QaToolCapabilityProfileLoader}'s classpath/SnakeYaml idiom, scoped to
 * {@code website-qa-agent} for the same reason that loader is: this file's shape is specific to
 * Website QA V1, not a generic agent concept.
 *
 * <p>{@code capability: PLATFORM_CORE} rows are loaded with an empty {@link
 * QaCheckRegistryEntry#capability()} rather than rejected - AIW-171 already established that
 * {@code PLATFORM_CORE} is deliberately outside {@link QaToolCapability}'s own closed set.
 */
@Component
public class QaCheckRegistryLoader {

	private static final String REGISTRY_PATTERN = "classpath*:project-types/**/website-qa-agent/registries/qa-checks.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	private volatile QaCheckRegistry cached;

	QaCheckRegistryLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public QaCheckRegistry load() {
		QaCheckRegistry loaded = cached;
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
			resources = resourceResolver.getResources(REGISTRY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for the QA check registry", e);
		}
		if (resources.length != 1) {
			throw new IllegalStateException(
					"Expected exactly one QA check registry on the classpath, found " + resources.length);
		}
		return resources[0];
	}

	@SuppressWarnings("unchecked")
	private QaCheckRegistry parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidQaCheckRegistryException(resource, "Failed to read the QA check registry", e);
		}

		try {
			String ref = requireField(raw, "id") + "@" + requireField(raw, "version");
			List<Map<String, Object>> checksRaw = (List<Map<String, Object>>) requireField(raw, "checks");
			List<QaCheckRegistryEntry> entries = checksRaw.stream().map(this::toEntry).toList();
			return new QaCheckRegistry(ref, entries);
		} catch (RuntimeException e) {
			throw new InvalidQaCheckRegistryException(resource, "Malformed QA check registry", e);
		}
	}

	private QaCheckRegistryEntry toEntry(Map<String, Object> raw) {
		String code = (String) requireField(raw, "code");
		String domain = (String) requireField(raw, "domain");
		String capabilityRaw = (String) requireField(raw, "capability");
		QaCheckCategory category = QaCheckCategory.valueOf((String) requireField(raw, "category"));
		Optional<QaToolCapability> capability =
				"PLATFORM_CORE".equals(capabilityRaw) ? Optional.empty() : Optional.of(QaToolCapability.valueOf(capabilityRaw));
		return new QaCheckRegistryEntry(code, domain, capability, category);
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
