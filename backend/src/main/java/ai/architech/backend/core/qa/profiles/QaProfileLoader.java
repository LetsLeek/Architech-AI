package ai.architech.backend.core.qa.profiles;

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
 * Loads {@code website-qa-comparison-readiness@1.0.0} and {@code website-qa-full-release@1.0.0}
 * from {@code project-types/**}{@code /website-qa-agent/profiles/*.yaml} on the classpath
 * (AIW-175). That directory also holds {@code tool-capability-profile.v1.yaml} (AIW-171) - a
 * resource without a {@code profileType} field (this format's own distinguishing marker) is
 * skipped rather than treated as a malformed QA profile, mirroring {@code
 * AgentDefinitionLoader}'s own "scan broadly, resolve by matching field" idiom.
 */
@Component
public class QaProfileLoader {

	private static final String PROFILE_PATTERN = "classpath*:project-types/**/website-qa-agent/profiles/*.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	QaProfileLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public QaProfile resolve(String ref) {
		for (Resource resource : findProfileResources()) {
			Map<String, Object> raw = readRaw(resource);
			if (!raw.containsKey("profileType")) {
				continue;
			}
			QaProfile profile = toProfile(raw, resource);
			if (profile.ref().equals(ref)) {
				return profile;
			}
		}
		throw new QaProfileNotFoundException(ref);
	}

	private Resource[] findProfileResources() {
		try {
			return resourceResolver.getResources(PROFILE_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for QA profiles", e);
		}
	}

	private Map<String, Object> readRaw(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return yaml.load(in);
		} catch (IOException e) {
			throw new InvalidQaProfileException(resource, "Failed to read QA profile", e);
		}
	}

	@SuppressWarnings("unchecked")
	private QaProfile toProfile(Map<String, Object> raw, Resource resource) {
		try {
			String ref = requireField(raw, "id") + "@" + requireField(raw, "version");
			QaProfileType profileType = QaProfileType.valueOf((String) requireField(raw, "profileType"));

			Map<String, Object> testContexts = requireMap(raw, "testContexts");
			List<Map<String, Object>> viewportsRaw = (List<Map<String, Object>>) requireField(testContexts, "viewports");
			List<QaViewport> viewports = viewportsRaw.stream()
					.map(v -> new QaViewport((String) requireField(v, "id"), (Integer) requireField(v, "width"), (Integer) requireField(v, "height")))
					.toList();

			Map<String, Object> preconditions = requireMap(raw, "preconditions");
			List<String> preconditionChecks = (List<String>) requireField(preconditions, "requiredChecks");

			Map<String, Object> domainsRaw = requireMap(raw, "domains");
			List<QaDomainDefinition> domains = domainsRaw.entrySet().stream()
					.map(entry -> toDomainDefinition(entry.getKey(), (Map<String, Object>) entry.getValue()))
					.toList();

			return new QaProfile(ref, profileType, viewports, preconditionChecks, domains);
		} catch (RuntimeException e) {
			throw new InvalidQaProfileException(resource, "Malformed QA profile", e);
		}
	}

	@SuppressWarnings("unchecked")
	private QaDomainDefinition toDomainDefinition(String domainName, Map<String, Object> raw) {
		QaDomainApplicability applicability = QaDomainApplicability.valueOf((String) requireField(raw, "applicability"));
		String conditionKey = (String) raw.get("when");
		List<String> requiredChecks = (List<String>) raw.getOrDefault("requiredChecks", List.of());
		List<String> evidenceChecks = (List<String>) raw.getOrDefault("evidenceChecks", List.of());
		return new QaDomainDefinition(domainName, applicability, conditionKey, requiredChecks, evidenceChecks);
	}

	private static Map<String, Object> requireMap(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (!(value instanceof Map)) {
			throw new IllegalStateException("Missing required object field: " + field);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) value;
		return map;
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
