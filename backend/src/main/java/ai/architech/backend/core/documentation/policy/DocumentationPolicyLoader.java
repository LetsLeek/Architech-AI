package ai.architech.backend.core.documentation.policy;

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
 * Loads the frozen {@code policies/documentation-policy.yaml} into a {@link DocumentationPolicy}
 * (AIW-188), mirroring {@code FindingTaxonomyLoader}'s singleton-classpath-resource idiom.
 */
@Component
public class DocumentationPolicyLoader {

	private static final String POLICY_PATTERN = "classpath*:project-types/**/documentation-agent/policies/documentation-policy.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	private volatile DocumentationPolicy cached;

	DocumentationPolicyLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public DocumentationPolicy load() {
		DocumentationPolicy loaded = cached;
		if (loaded != null) {
			return loaded;
		}
		Resource resource = findPolicyResource();
		loaded = parse(resource);
		cached = loaded;
		return loaded;
	}

	private Resource findPolicyResource() {
		Resource[] resources;
		try {
			resources = resourceResolver.getResources(POLICY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for the Documentation policy", e);
		}
		if (resources.length != 1) {
			throw new IllegalStateException("Expected exactly one Documentation policy on the classpath, found " + resources.length);
		}
		return resources[0];
	}

	@SuppressWarnings("unchecked")
	private DocumentationPolicy parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidDocumentationPolicyException(resource, "Failed to read the Documentation policy", e);
		}

		try {
			String ref = requireField(raw, "policyId") + "@" + requireField(raw, "version");
			SecurityPolicy security = toSecurityPolicy(requireMap(raw, "security"));
			EpistemicPolicy epistemic = toEpistemicPolicy(requireMap(raw, "epistemic"));
			FindingDisclosurePolicy findingDisclosure = toFindingDisclosurePolicy(requireMap(raw, "findingDisclosure"));
			ValidationPolicy validation = toValidationPolicy(requireMap(raw, "validation"));
			ProfileSpecificOverrides profileSpecificOverrides = toProfileSpecificOverrides(requireMap(raw, "profileSpecificOverrides"));
			return new DocumentationPolicy(
					ref, security, epistemic, findingDisclosure, validation, profileSpecificOverrides, (String) requireField(raw, "note"));
		} catch (RuntimeException e) {
			throw new InvalidDocumentationPolicyException(resource, "Malformed Documentation policy", e);
		}
	}

	private SecurityPolicy toSecurityPolicy(Map<String, Object> raw) {
		return new SecurityPolicy(
				(Boolean) requireField(raw, "forbidSecretValues"),
				(Boolean) requireField(raw, "fieldLevelMinimization"),
				(Boolean) requireField(raw, "preModelSecurityRequired"),
				(Boolean) requireField(raw, "postGenerationSecurityBeforeSemanticModel"),
				(Boolean) requireField(raw, "failClosed"),
				(Boolean) requireField(raw, "audienceClassificationIndependent"));
	}

	private EpistemicPolicy toEpistemicPolicy(Map<String, Object> raw) {
		return new EpistemicPolicy(
				(String) requireField(raw, "unknown"),
				(String) requireField(raw, "missingOptionalAuthority"),
				(String) requireField(raw, "authorityConflict"),
				(String) requireField(raw, "lineageIncompatibility"),
				(Boolean) requireField(raw, "noInference"));
	}

	@SuppressWarnings("unchecked")
	private FindingDisclosurePolicy toFindingDisclosurePolicy(Map<String, Object> raw) {
		CustomerDisclosureRules customer = toCustomerDisclosureRules(requireMap(raw, "customer"));
		DeveloperDisclosureRules developer = toDeveloperDisclosureRules(requireMap(raw, "developer"));
		return new FindingDisclosurePolicy(
				(Boolean) requireField(raw, "onlyCurrentCandidateFindings"), (Boolean) requireField(raw, "qaGateDoesNotEraseFindings"), customer, developer);
	}

	private CustomerDisclosureRules toCustomerDisclosureRules(Map<String, Object> raw) {
		return new CustomerDisclosureRules(
				(String) requireField(raw, "qaRequiredProfile"),
				(String) requireField(raw, "qaRequiredGate"),
				(String) requireField(raw, "materialCustomerImpact"),
				(String) requireField(raw, "blockingOrUnresolvedEscalation"),
				(String) requireField(raw, "resolvedHistorical"),
				(String) requireField(raw, "notEvaluableMaterial"),
				(String) requireField(raw, "technicalOnlyNonmaterial"),
				(String) requireField(raw, "unmappedMaterialFallback"));
	}

	@SuppressWarnings("unchecked")
	private DeveloperDisclosureRules toDeveloperDisclosureRules(Map<String, Object> raw) {
		return new DeveloperDisclosureRules(
				(String) requireField(raw, "qaRequiredProfile"),
				(List<String>) requireField(raw, "qaAllowedGates"),
				(String) requireField(raw, "allTechnicallyRelevantCurrent"),
				(String) requireField(raw, "materialSecurityDetail"),
				(String) requireField(raw, "resolvedHistorical"),
				(String) requireField(raw, "unmappedCurrentFallback"));
	}

	private ValidationPolicy toValidationPolicy(Map<String, Object> raw) {
		return new ValidationPolicy(
				(Boolean) requireField(raw, "semanticFactualConsistencyRequired"),
				(Boolean) requireField(raw, "unsupportedClaimBlocks"),
				(Boolean) requireField(raw, "notEvaluableBlocks"),
				(Boolean) requireField(raw, "requiredDisclosureSemanticFidelity"),
				(Boolean) requireField(raw, "includeBoundedCounterfacts"));
	}

	private ProfileSpecificOverrides toProfileSpecificOverrides(Map<String, Object> raw) {
		return new ProfileSpecificOverrides(
				(Boolean) requireField(raw, "allowWeakeningGlobalAuthority"),
				(Boolean) requireField(raw, "allowSecrets"),
				(Boolean) requireField(raw, "allowBypassSemanticValidation"));
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
