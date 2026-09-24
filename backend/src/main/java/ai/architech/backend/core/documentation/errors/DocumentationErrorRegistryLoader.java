package ai.architech.backend.core.documentation.errors;

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
 * Loads the frozen {@code registries/documentation-error-registry.yaml} into a {@link
 * DocumentationErrorRegistry} (AIW-188), mirroring {@code FindingTaxonomyLoader}'s
 * singleton-classpath-resource idiom.
 */
@Component
public class DocumentationErrorRegistryLoader {

	private static final String ERROR_REGISTRY_PATTERN =
			"classpath*:project-types/**/documentation-agent/registries/documentation-error-registry.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	private volatile DocumentationErrorRegistry cached;

	DocumentationErrorRegistryLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public DocumentationErrorRegistry load() {
		DocumentationErrorRegistry loaded = cached;
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
			resources = resourceResolver.getResources(ERROR_REGISTRY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for the Documentation error registry", e);
		}
		if (resources.length != 1) {
			throw new IllegalStateException("Expected exactly one Documentation error registry on the classpath, found " + resources.length);
		}
		return resources[0];
	}

	@SuppressWarnings("unchecked")
	private DocumentationErrorRegistry parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidDocumentationErrorRegistryException(resource, "Failed to read the Documentation error registry", e);
		}

		try {
			List<Map<String, Object>> errorsRaw = (List<Map<String, Object>>) requireField(raw, "errors");
			List<DocumentationErrorCode> errors = errorsRaw.stream().map(this::toErrorCode).toList();
			return new DocumentationErrorRegistry(
					(String) requireField(raw, "registryVersion"), (List<String>) requireField(raw, "notes"), errors);
		} catch (RuntimeException e) {
			throw new InvalidDocumentationErrorRegistryException(resource, "Malformed Documentation error registry", e);
		}
	}

	private DocumentationErrorCode toErrorCode(Map<String, Object> raw) {
		return new DocumentationErrorCode(
				(String) requireField(raw, "code"),
				(String) requireField(raw, "issueClass"),
				(String) requireField(raw, "defaultRemediationTarget"),
				(String) requireField(raw, "retryPolicy"),
				(Boolean) requireField(raw, "blocking"));
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
