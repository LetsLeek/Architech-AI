package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

/**
 * Proves AIW-187's actual point: the frozen Documentation Agent V1 schema family resolves
 * correctly through {@link DocumentationSchemaRegistry} once every schema carries a real {@code
 * urn:aiw:schema:documentation:*:v1} {@code $id} and every relative/bare-filename {@code $ref}
 * from the raw package is retrofitted onto one - mirrors {@link WebsiteQaSchemaRegistryIT}'s own
 * emphasis on proving the composition, not just individual schemas in isolation.
 *
 * <p>The two golden fixtures used here ({@code customer-pass-context.json}, {@code
 * customer-pass-semantic-candidate.json}) are copied unmodified from the frozen package's own
 * {@code fixtures/golden/} - real, hand-authored CUSTOMER_HANDOVER examples the package ships
 * specifically as schema-valid references, not synthesized for this test. The context fixture
 * alone exercises five of the family's cross-file {@code $ref}s in one document
 * ({@code artifact-ref}, {@code authority-catalog-entry}, {@code safe-fact}, {@code
 * context-state}, {@code finding-disclosure-view}) and includes real {@code MISSING_AUTHORITY}/
 * {@code NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED} context-state entries - the exact mechanism
 * DECISION_LOG.md documents for representing "no ApprovalRecord bound to this context" without
 * inventing a Product Authority type that doesn't exist upstream.
 */
@SpringBootTest
class DocumentationSchemaRegistryIT {

	private static final String CUSTOMER_CONTEXT_FIXTURE = "classpath:fixtures/documentation-agent/customer-pass-context.json";
	private static final String CUSTOMER_SEMANTIC_CANDIDATE_FIXTURE =
			"classpath:fixtures/documentation-agent/customer-pass-semantic-candidate.json";

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private DocumentationSchemaRegistry registry;

	@Test
	void acceptsTheFrozenCustomerPassContextFixtureResolvingEveryCrossFileRef() {
		SchemaValidationResult result = registry.validate(
				"urn:aiw:schema:documentation:documentation-context:v1", readClasspath(CUSTOMER_CONTEXT_FIXTURE));

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void acceptsTheFrozenCustomerPassSemanticCandidateFixture() {
		SchemaValidationResult result = registry.validate(
				"urn:aiw:schema:documentation:semantic-documentation-candidate:v1",
				readClasspath(CUSTOMER_SEMANTIC_CANDIDATE_FIXTURE));

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsAContextMissingARequiredTopLevelField() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "contextId": "DC-1",
				  "contextVersion": 1
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:documentation:documentation-context:v1", json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	@Test
	void rejectsASemanticCandidateAttemptingToSmuggleACoreOwnedCanonicalFlag() {
		String json =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [],
				  "canonical": true
				}
				""";

		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:documentation:semantic-documentation-candidate:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void reportsInvalidJsonRatherThanThrowing() {
		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:documentation:documentation-context:v1", "not json {{{");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}

	@Test
	void resolvesTheContextStateCrossFileReferenceIncludingMissingAuthorityAndNoDisclosableFindingsKinds() {
		String json = readClasspath(CUSTOMER_CONTEXT_FIXTURE);

		SchemaValidationResult result = registry.validate("urn:aiw:schema:documentation:documentation-context:v1", json);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
		// The fixture's own contextStates array carries a MISSING_AUTHORITY entry for DEPLOYMENT
		// and a NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED entry for QA_FINDING - schema-accepting
		// this document proves both context-state.schema.json's own cross-file $ref and its
		// "kind" enum resolve correctly through this registry, not just documentation-context's
		// own top-level shape.
		assertThat(json).contains("\"kind\": \"MISSING_AUTHORITY\"");
		assertThat(json).contains("\"kind\": \"NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED\"");
	}

	private String readClasspath(String location) {
		try {
			return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
