package ai.architech.backend.core.qa.invariants;

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
 * Loads the frozen {@code registries/finding-codes.yaml} into a {@link FindingTaxonomy}
 * (AIW-174), mirroring {@code QaCheckRegistryLoader}'s classpath/SnakeYaml idiom - the ref this
 * produces matches {@code QAExecutionPreflightValidator.EXPECTED_FINDING_TAXONOMY_REF}.
 */
@Component
public class FindingTaxonomyLoader {

	private static final String TAXONOMY_PATTERN = "classpath*:project-types/**/website-qa-agent/registries/finding-codes.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	private volatile FindingTaxonomy cached;

	FindingTaxonomyLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public FindingTaxonomy load() {
		FindingTaxonomy loaded = cached;
		if (loaded != null) {
			return loaded;
		}
		Resource resource = findTaxonomyResource();
		loaded = parse(resource);
		cached = loaded;
		return loaded;
	}

	private Resource findTaxonomyResource() {
		Resource[] resources;
		try {
			resources = resourceResolver.getResources(TAXONOMY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for the QA finding taxonomy", e);
		}
		if (resources.length != 1) {
			throw new IllegalStateException("Expected exactly one QA finding taxonomy on the classpath, found " + resources.length);
		}
		return resources[0];
	}

	@SuppressWarnings("unchecked")
	private FindingTaxonomy parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidFindingTaxonomyException(resource, "Failed to read the QA finding taxonomy", e);
		}

		try {
			String ref = requireField(raw, "id") + "@" + requireField(raw, "version");
			List<Map<String, Object>> findingsRaw = (List<Map<String, Object>>) requireField(raw, "findings");
			List<FindingTaxonomyEntry> entries = findingsRaw.stream().map(this::toEntry).toList();
			return new FindingTaxonomy(ref, entries);
		} catch (RuntimeException e) {
			throw new InvalidFindingTaxonomyException(resource, "Malformed QA finding taxonomy", e);
		}
	}

	@SuppressWarnings("unchecked")
	private FindingTaxonomyEntry toEntry(Map<String, Object> raw) {
		return new FindingTaxonomyEntry(
				(String) requireField(raw, "code"),
				(String) requireField(raw, "domain"),
				QaSeverity.valueOf((String) requireField(raw, "minimumSeverity")),
				QaSeverity.valueOf((String) requireField(raw, "maximumSeverity")),
				(List<String>) requireField(raw, "allowedNormativeBasisTypes"));
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
