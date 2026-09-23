package ai.architech.backend.core.documentation.policy;

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
 * Loads the frozen {@code policies/documentation-workflow-policy.yaml} into a {@link
 * DocumentationWorkflowPolicy} (AIW-188), mirroring {@code FindingTaxonomyLoader}'s
 * singleton-classpath-resource idiom.
 */
@Component
public class DocumentationWorkflowPolicyLoader {

	private static final String WORKFLOW_POLICY_PATTERN =
			"classpath*:project-types/**/documentation-agent/policies/documentation-workflow-policy.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	private volatile DocumentationWorkflowPolicy cached;

	DocumentationWorkflowPolicyLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public DocumentationWorkflowPolicy load() {
		DocumentationWorkflowPolicy loaded = cached;
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
			resources = resourceResolver.getResources(WORKFLOW_POLICY_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for the Documentation workflow policy", e);
		}
		if (resources.length != 1) {
			throw new IllegalStateException(
					"Expected exactly one Documentation workflow policy on the classpath, found " + resources.length);
		}
		return resources[0];
	}

	@SuppressWarnings("unchecked")
	private DocumentationWorkflowPolicy parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidDocumentationPolicyException(resource, "Failed to read the Documentation workflow policy", e);
		}

		try {
			String ref = requireField(raw, "workflowPolicyId") + "@" + requireField(raw, "version");

			List<Map<String, Object>> enabledTriggersRaw = (List<Map<String, Object>>) requireField(raw, "enabledTriggers");
			List<WorkflowTrigger> enabledTriggers = enabledTriggersRaw.stream().map(this::toWorkflowTrigger).toList();

			List<Map<String, Object>> onDemandRaw = (List<Map<String, Object>>) requireField(raw, "onDemand");
			List<WorkflowTrigger> onDemand = onDemandRaw.stream().map(this::toWorkflowTrigger).toList();

			return new DocumentationWorkflowPolicy(
					ref,
					enabledTriggers,
					onDemand,
					(List<String>) requireField(raw, "optionalRefreshEvents"),
					(List<String>) requireField(raw, "ignoredAutomaticTriggers"),
					(Boolean) requireField(raw, "triggerIdempotencyRequired"),
					(Boolean) requireField(raw, "productGateDependency"),
					(Integer) requireField(raw, "maxGenerationAttempts"),
					(Integer) requireField(raw, "maxSemanticEvaluatorExecutionsPerUnchangedCandidate"));
		} catch (RuntimeException e) {
			throw new InvalidDocumentationPolicyException(resource, "Malformed Documentation workflow policy", e);
		}
	}

	@SuppressWarnings("unchecked")
	private WorkflowTrigger toWorkflowTrigger(Map<String, Object> raw) {
		return new WorkflowTrigger(
				Optional.ofNullable((String) raw.get("event")), (String) requireField(raw, "profileRef"), (List<String>) requireField(raw, "conditions"));
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
