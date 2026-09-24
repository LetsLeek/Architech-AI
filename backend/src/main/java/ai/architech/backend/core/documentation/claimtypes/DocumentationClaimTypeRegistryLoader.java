package ai.architech.backend.core.documentation.claimtypes;

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
 * Loads the frozen {@code registries/claim-types.yaml} into a {@link DocumentationClaimTypeRegistry}
 * (AIW-196), mirroring {@code DocumentationErrorRegistryLoader}'s singleton-classpath-resource idiom.
 */
@Component
public class DocumentationClaimTypeRegistryLoader {

	private static final String CLAIM_TYPE_REGISTRY_PATTERN =
			"classpath*:project-types/**/documentation-agent/registries/claim-types.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	private volatile DocumentationClaimTypeRegistry cached;

	DocumentationClaimTypeRegistryLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public DocumentationClaimTypeRegistry load() {
		DocumentationClaimTypeRegistry loaded = cached;
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
			resources = resourceResolver.getResources(CLAIM_TYPE_REGISTRY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for the Documentation claim-type registry", e);
		}
		if (resources.length != 1) {
			throw new IllegalStateException(
					"Expected exactly one Documentation claim-type registry on the classpath, found " + resources.length);
		}
		return resources[0];
	}

	@SuppressWarnings("unchecked")
	private DocumentationClaimTypeRegistry parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidDocumentationClaimTypeRegistryException(resource, "Failed to read the Documentation claim-type registry", e);
		}

		try {
			List<Map<String, Object>> claimTypesRaw = (List<Map<String, Object>>) requireField(raw, "claimTypes");
			List<DocumentationClaimType> claimTypes = claimTypesRaw.stream().map(this::toClaimType).toList();
			return new DocumentationClaimTypeRegistry((String) requireField(raw, "registryVersion"), claimTypes);
		} catch (RuntimeException e) {
			throw new InvalidDocumentationClaimTypeRegistryException(resource, "Malformed Documentation claim-type registry", e);
		}
	}

	@SuppressWarnings("unchecked")
	private DocumentationClaimType toClaimType(Map<String, Object> raw) {
		List<String> domainMinimum = (List<String>) raw.getOrDefault("domainMinimum", List.of());
		return new DocumentationClaimType((String) requireField(raw, "id"), domainMinimum);
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
