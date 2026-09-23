package ai.architech.backend.core.documentation.profiles;

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
 * Loads {@code CUSTOMER_HANDOVER@1.0.0} and {@code TECHNICAL_HANDOVER@1.0.0} from {@code
 * project-types/**}{@code /documentation-agent/profiles/*.yaml} on the classpath (AIW-188),
 * mirroring {@code QaProfileLoader}'s own "scan broadly, resolve by exact ref" idiom.
 */
@Component
public class DocumentationProfileLoader {

	private static final String PROFILE_PATTERN = "classpath*:project-types/**/documentation-agent/profiles/*.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	DocumentationProfileLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public DocumentationProfile resolve(String ref) {
		for (Resource resource : findProfileResources()) {
			Map<String, Object> raw = readRaw(resource);
			DocumentationProfile profile = toProfile(raw, resource);
			if (profile.ref().equals(ref)) {
				return profile;
			}
		}
		throw new DocumentationProfileNotFoundException(ref);
	}

	private Resource[] findProfileResources() {
		try {
			return resourceResolver.getResources(PROFILE_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for Documentation profiles", e);
		}
	}

	private Map<String, Object> readRaw(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return yaml.load(in);
		} catch (IOException e) {
			throw new InvalidDocumentationProfileException(resource, "Failed to read Documentation profile", e);
		}
	}

	@SuppressWarnings("unchecked")
	private DocumentationProfile toProfile(Map<String, Object> raw, Resource resource) {
		try {
			String ref = requireField(raw, "profileId") + "@" + requireField(raw, "version");
			DocumentationProfileType profileType = DocumentationProfileType.valueOf((String) requireField(raw, "profileId"));

			DocumentationQaPreconditions qaPreconditions = toQaPreconditions(requireMap(raw, "qaPreconditions"));

			Optional<List<String>> candidateSafeProjectionRequired = raw.containsKey("candidateSafeProjectionRequired")
					? Optional.of((List<String>) raw.get("candidateSafeProjectionRequired"))
					: Optional.empty();

			List<Map<String, Object>> semanticDocumentsRaw = (List<Map<String, Object>>) requireField(raw, "semanticDocuments");
			List<SemanticDocumentSpec> semanticDocuments = semanticDocumentsRaw.stream().map(this::toSemanticDocumentSpec).toList();

			List<Map<String, Object>> deterministicReportsRaw = (List<Map<String, Object>>) requireField(raw, "deterministicReports");
			List<DeterministicReportSpec> deterministicReports = deterministicReportsRaw.stream().map(this::toDeterministicReportSpec).toList();

			Map<String, Object> composerDeterministicBlocksRaw = requireMap(raw, "composerDeterministicBlocks");
			Map<String, List<String>> composerDeterministicBlocks = composerDeterministicBlocksRaw.entrySet().stream()
					.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> (List<String>) e.getValue()));

			return new DocumentationProfile(
					ref,
					profileType,
					(Boolean) requireField(raw, "active"),
					(String) requireField(raw, "primaryAudience"),
					(String) requireField(raw, "operation"),
					(List<String>) requireField(raw, "requiredRoots"),
					(List<String>) requireField(raw, "optionalRoots"),
					qaPreconditions,
					candidateSafeProjectionRequired,
					semanticDocuments,
					deterministicReports,
					composerDeterministicBlocks,
					(List<String>) requireField(raw, "allowedClaimTypes"),
					(String) requireField(raw, "defaultSkillSet"),
					(String) requireField(raw, "compositionSkill"),
					(Boolean) requireField(raw, "semanticValidationRequired"),
					(List<String>) requireField(raw, "supportedLocales"),
					(String) requireField(raw, "findingPolicyRef"));
		} catch (RuntimeException e) {
			throw new InvalidDocumentationProfileException(resource, "Malformed Documentation profile", e);
		}
	}

	private DocumentationQaPreconditions toQaPreconditions(Map<String, Object> raw) {
		return new DocumentationQaPreconditions((String) requireField(raw, "qaProfile"), (List<String>) requireField(raw, "allowedGates"));
	}

	@SuppressWarnings("unchecked")
	private SemanticDocumentSpec toSemanticDocumentSpec(Map<String, Object> raw) {
		List<Map<String, Object>> sectionsRaw = (List<Map<String, Object>>) requireField(raw, "sections");
		List<DocumentSectionSpec> sections = sectionsRaw.stream()
				.map(s -> new DocumentSectionSpec(
						(String) requireField(s, "sectionType"),
						(Boolean) requireField(s, "required"),
						(Boolean) requireField(s, "allowEmptyAgentBlocks")))
				.toList();
		return new SemanticDocumentSpec((String) requireField(raw, "documentType"), (Boolean) requireField(raw, "required"), sections);
	}

	private DeterministicReportSpec toDeterministicReportSpec(Map<String, Object> raw) {
		return new DeterministicReportSpec(
				(String) requireField(raw, "reportType"),
				(Boolean) requireField(raw, "required"),
				(String) requireField(raw, "deliveryDisposition"));
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
