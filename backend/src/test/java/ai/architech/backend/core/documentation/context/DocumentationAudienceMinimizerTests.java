package ai.architech.backend.core.documentation.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proves {@link DocumentationAudienceMinimizer}'s V1 policy against synthetic fixtures - no real
 * {@code SENSITIVE_DOCUMENTABLE}/{@code REFERENCE_ONLY} fact producer exists yet (AIW-191).
 */
class DocumentationAudienceMinimizerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final DocumentationAudienceMinimizer minimizer = new DocumentationAudienceMinimizer(objectMapper);

	@Test
	void customerAudienceDropsSensitiveFactsAndPrunesTheirKeyFromTheCatalog() {
		ArrayNode facts = threeClassifiedFacts();
		ArrayNode catalog = catalogReferencingAllThree();

		DocumentationAudienceMinimizer.MinimizedContent minimized = minimizer.minimize(facts, catalog, "CUSTOMER");

		List<String> remainingFactKeys = factKeysOf(minimized.resolvedFacts());
		assertThat(remainingFactKeys).containsExactlyInAnyOrder("F_PUBLIC", "F_REFERENCE");

		JsonNode catalogEntry = minimized.authorityCatalog().get(0);
		List<String> safeFactKeys = safeFactKeysOf(catalogEntry);
		assertThat(safeFactKeys).containsExactlyInAnyOrder("F_PUBLIC", "F_REFERENCE");
	}

	@Test
	void developerAudienceKeepsAllThreeClassifications() {
		ArrayNode facts = threeClassifiedFacts();
		ArrayNode catalog = catalogReferencingAllThree();

		DocumentationAudienceMinimizer.MinimizedContent minimized = minimizer.minimize(facts, catalog, "DEVELOPER");

		assertThat(factKeysOf(minimized.resolvedFacts())).containsExactlyInAnyOrder("F_PUBLIC", "F_SENSITIVE", "F_REFERENCE");
		assertThat(safeFactKeysOf(minimized.authorityCatalog().get(0)))
				.containsExactlyInAnyOrder("F_PUBLIC", "F_SENSITIVE", "F_REFERENCE");
	}

	private ArrayNode threeClassifiedFacts() {
		ArrayNode facts = objectMapper.createArrayNode();
		facts.add(fact("F_PUBLIC", "PUBLIC_DOCUMENTABLE"));
		facts.add(fact("F_SENSITIVE", "SENSITIVE_DOCUMENTABLE"));
		facts.add(fact("F_REFERENCE", "REFERENCE_ONLY"));
		return facts;
	}

	private ArrayNode catalogReferencingAllThree() {
		ArrayNode catalog = objectMapper.createArrayNode();
		ObjectNode entry = objectMapper.createObjectNode();
		entry.put("key", "AUTH1");
		entry.set("authorityRef", objectMapper.createObjectNode());
		ArrayNode safeFactKeys = objectMapper.createArrayNode();
		safeFactKeys.add("F_PUBLIC");
		safeFactKeys.add("F_SENSITIVE");
		safeFactKeys.add("F_REFERENCE");
		entry.set("safeFactKeys", safeFactKeys);
		catalog.add(entry);
		return catalog;
	}

	private ObjectNode fact(String factKey, String classification) {
		ObjectNode fact = objectMapper.createObjectNode();
		fact.put("factKey", factKey);
		fact.put("classification", classification);
		return fact;
	}

	private List<String> factKeysOf(JsonNode facts) {
		return facts.valueStream().map(n -> n.path("factKey").asString()).toList();
	}

	private List<String> safeFactKeysOf(JsonNode catalogEntry) {
		return catalogEntry.path("safeFactKeys").valueStream().map(JsonNode::asString).toList();
	}
}
