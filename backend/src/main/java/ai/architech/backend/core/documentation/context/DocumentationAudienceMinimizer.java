package ai.architech.backend.core.documentation.context;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pre-model audience minimization over an about-to-be-frozen {@code DocumentationContext}'s
 * {@code resolvedFacts}/{@code authorityCatalog} arrays (AIW-191, {@code DOC-SEC-002}/{@code
 * DOC-SEC-003} - fact {@code classification} is computed independent of audience elsewhere, but
 * this step still decides, per audience, which classifications are actually allowed through
 * before the context is frozen).
 *
 * <p><b>V1 policy</b> (a real decision this ticket makes, not one spelled out verbatim anywhere in
 * the frozen package - no real {@code SENSITIVE_DOCUMENTABLE}/{@code REFERENCE_ONLY} fact exists
 * in production yet, since {@link DocumentationContextAssembler}'s own bounded fact set is entirely
 * {@code PUBLIC_DOCUMENTABLE} today; this class is exercised only against synthetic fixtures until
 * a real non-public fact producer exists): {@code CUSTOMER} audience keeps {@code
 * PUBLIC_DOCUMENTABLE} and {@code REFERENCE_ONLY} facts and drops {@code SENSITIVE_DOCUMENTABLE}
 * ones entirely; {@code DEVELOPER} audience keeps all three. A dropped fact's key is also pruned
 * from every {@code authorityCatalog} entry's {@code safeFactKeys} list, so no catalog entry is
 * left pointing at a fact that no longer exists in {@code resolvedFacts}.
 */
@Component
public class DocumentationAudienceMinimizer {

	private static final String CUSTOMER_AUDIENCE = "CUSTOMER";
	private static final String SENSITIVE_DOCUMENTABLE = "SENSITIVE_DOCUMENTABLE";

	private final ObjectMapper objectMapper;

	DocumentationAudienceMinimizer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public record MinimizedContent(ArrayNode resolvedFacts, ArrayNode authorityCatalog) {}

	public MinimizedContent minimize(ArrayNode resolvedFacts, ArrayNode authorityCatalog, String primaryAudience) {
		Set<String> droppedFactKeys = new HashSet<>();
		ArrayNode filteredFacts = objectMapper.createArrayNode();
		for (JsonNode fact : resolvedFacts) {
			if (isDropped(fact, primaryAudience)) {
				droppedFactKeys.add(fact.path("factKey").asString());
			} else {
				filteredFacts.add(fact);
			}
		}

		ArrayNode filteredCatalog = objectMapper.createArrayNode();
		for (JsonNode entry : authorityCatalog) {
			filteredCatalog.add(withPrunedSafeFactKeys(entry, droppedFactKeys));
		}

		return new MinimizedContent(filteredFacts, filteredCatalog);
	}

	private boolean isDropped(JsonNode fact, String primaryAudience) {
		String classification = fact.path("classification").asString();
		return CUSTOMER_AUDIENCE.equals(primaryAudience) && SENSITIVE_DOCUMENTABLE.equals(classification);
	}

	private ObjectNode withPrunedSafeFactKeys(JsonNode entry, Set<String> droppedFactKeys) {
		ObjectNode copy = objectMapper.createObjectNode();
		copy.put("key", entry.path("key").asString());
		copy.set("authorityRef", entry.get("authorityRef"));

		ArrayNode safeFactKeys = objectMapper.createArrayNode();
		for (JsonNode keyNode : entry.path("safeFactKeys")) {
			if (!droppedFactKeys.contains(keyNode.asString())) {
				safeFactKeys.add(keyNode.asString());
			}
		}
		copy.set("safeFactKeys", safeFactKeys);
		return copy;
	}
}
