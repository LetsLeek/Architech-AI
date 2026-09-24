package ai.architech.backend.core.documentation.context;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proves {@link DocumentationSecretScanner} actually walks every position it claims to (a {@code
 * safe-value} {@code STRING}, a {@code STRING_LIST} entry, and an {@code authorityCatalog} entry's
 * {@code authorityRef.locator.value}) rather than only the happy "nothing found" path.
 */
class DocumentationSecretScannerTests {

	// AKIA + 16 alphanumerics: structurally matches SecretPatterns.AWS_ACCESS_KEY, an unambiguous
	// fake test placeholder, never a real key.
	private static final String FAKE_SECRET = "AKIAFAKETESTKEY12345";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final DocumentationSecretScanner scanner = new DocumentationSecretScanner();

	@Test
	void passesWhenNothingMatchesAnyKnownSecretPattern() {
		ArrayNode facts = objectMapper.createArrayNode();
		facts.add(stringFact("F1", "ordinary safe text"));
		ArrayNode catalog = objectMapper.createArrayNode();
		catalog.add(catalogEntry("AUTH1", "business.name"));

		assertThatCode(() -> scanner.scan(facts, catalog)).doesNotThrowAnyException();
	}

	@Test
	void detectsASecretShapeInAStringFactValue() {
		ArrayNode facts = objectMapper.createArrayNode();
		facts.add(stringFact("F1", FAKE_SECRET));
		ArrayNode catalog = objectMapper.createArrayNode();

		assertThatThrownBy(() -> scanner.scan(facts, catalog))
				.isInstanceOf(DocumentationSecretLeakageDetectedException.class)
				.hasMessageContaining("AWS_ACCESS_KEY")
				.hasMessageContaining("F1")
				.hasMessageNotContaining(FAKE_SECRET);
	}

	@Test
	void detectsASecretShapeInsideAStringListFactValue() {
		ObjectNode fact = objectMapper.createObjectNode();
		fact.put("factKey", "F2");
		ObjectNode value = objectMapper.createObjectNode();
		value.put("valueType", "STRING_LIST");
		ArrayNode list = objectMapper.createArrayNode();
		list.add("safe entry");
		list.add(FAKE_SECRET);
		value.set("value", list);
		fact.set("value", value);

		ArrayNode facts = objectMapper.createArrayNode();
		facts.add(fact);
		ArrayNode catalog = objectMapper.createArrayNode();

		assertThatThrownBy(() -> scanner.scan(facts, catalog))
				.isInstanceOf(DocumentationSecretLeakageDetectedException.class)
				.hasMessageContaining("AWS_ACCESS_KEY")
				.hasMessageNotContaining(FAKE_SECRET);
	}

	@Test
	void detectsASecretShapeInAnAuthorityCatalogLocatorValue() {
		ArrayNode facts = objectMapper.createArrayNode();
		ArrayNode catalog = objectMapper.createArrayNode();
		catalog.add(catalogEntry("AUTH1", FAKE_SECRET));

		assertThatThrownBy(() -> scanner.scan(facts, catalog))
				.isInstanceOf(DocumentationSecretLeakageDetectedException.class)
				.hasMessageContaining("AWS_ACCESS_KEY")
				.hasMessageContaining("AUTH1")
				.hasMessageNotContaining(FAKE_SECRET);
	}

	@Test
	void scanCandidatePassesForACleanMultiSectionMultiClaimCandidate() {
		JsonNode candidate = objectMapper.readTree(
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [
				    {
				      "documentType": "CUSTOMER_WEBSITE_HANDOVER",
				      "sections": [
				        {"sectionType": "WEBSITE_OVERVIEW", "blocks": [
				          {"blockType": "NARRATIVE", "claims": [
				            {"claimKey": "C-1", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "ordinary safe text", "authorityKeys": ["AUTH-1"]}
				          ]}
				        ]},
				        {"sectionType": "FEATURES", "blocks": [
				          {"blockType": "LIST", "items": [
				            {"claims": [
				              {"claimKey": "C-2", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "another safe claim", "authorityKeys": ["AUTH-1"]}
				            ]}
				          ]}
				        ]}
				      ]
				    }
				  ]
				}
				""");

		assertThatCode(() -> scanner.scanCandidate(candidate)).doesNotThrowAnyException();
	}

	@Test
	void scanCandidateDetectsASecretShapeInANarrativeBlockClaimText() {
		JsonNode candidate = objectMapper.readTree(
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [
				    {
				      "documentType": "CUSTOMER_WEBSITE_HANDOVER",
				      "sections": [
				        {"sectionType": "WEBSITE_OVERVIEW", "blocks": [
				          {"blockType": "NARRATIVE", "claims": [
				            {"claimKey": "C-1", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "%s", "authorityKeys": ["AUTH-1"]}
				          ]}
				        ]}
				      ]
				    }
				  ]
				}
				"""
						.formatted(FAKE_SECRET));

		assertThatThrownBy(() -> scanner.scanCandidate(candidate))
				.isInstanceOf(DocumentationSecretLeakageDetectedException.class)
				.hasMessageContaining("AWS_ACCESS_KEY")
				.hasMessageNotContaining(FAKE_SECRET);
	}

	@Test
	void scanCandidateDetectsASecretShapeNestedInsideAListBlockItemClaimText() {
		JsonNode candidate = objectMapper.readTree(
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [
				    {
				      "documentType": "TECHNICAL_HANDOVER_GUIDE",
				      "sections": [
				        {"sectionType": "IMPLEMENTATION_OVERVIEW", "blocks": [
				          {"blockType": "LIST", "items": [
				            {"claims": [
				              {"claimKey": "C-1", "claimType": "IMPLEMENTATION_DESCRIPTION", "derivation": "DIRECT", "text": "safe", "authorityKeys": ["AUTH-1"]}
				            ]},
				            {"claims": [
				              {"claimKey": "C-2", "claimType": "IMPLEMENTATION_DESCRIPTION", "derivation": "DIRECT", "text": "%s", "authorityKeys": ["AUTH-1"]}
				            ]}
				          ]}
				        ]}
				      ]
				    }
				  ]
				}
				"""
						.formatted(FAKE_SECRET));

		assertThatThrownBy(() -> scanner.scanCandidate(candidate))
				.isInstanceOf(DocumentationSecretLeakageDetectedException.class)
				.hasMessageContaining("AWS_ACCESS_KEY")
				.hasMessageNotContaining(FAKE_SECRET);
	}

	private ObjectNode stringFact(String factKey, String value) {
		ObjectNode fact = objectMapper.createObjectNode();
		fact.put("factKey", factKey);
		ObjectNode valueNode = objectMapper.createObjectNode();
		valueNode.put("valueType", "STRING");
		valueNode.put("value", value);
		fact.set("value", valueNode);
		return fact;
	}

	private ObjectNode catalogEntry(String key, String locatorValue) {
		ObjectNode entry = objectMapper.createObjectNode();
		entry.put("key", key);
		ObjectNode authorityRef = objectMapper.createObjectNode();
		ObjectNode locator = objectMapper.createObjectNode();
		locator.put("kind", "OBJECT_ID");
		locator.put("value", locatorValue);
		authorityRef.set("locator", locator);
		entry.set("authorityRef", authorityRef);
		entry.set("safeFactKeys", objectMapper.createArrayNode());
		return entry;
	}
}
