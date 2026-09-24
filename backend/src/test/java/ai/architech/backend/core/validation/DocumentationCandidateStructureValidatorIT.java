package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

/**
 * Proves AIW-195's own scope: schema validity, "exactly one document of the required type"
 * identity, and document/section-level structural conformance (section membership, required-
 * coverage, {@code allowEmptyAgentBlocks}) - deliberately nothing deeper (no claim walk, no
 * section order, no key resolution - see {@link DocumentationCandidateStructureValidator}'s own
 * javadoc for the exact AIW-195/AIW-196 boundary).
 */
@SpringBootTest
class DocumentationCandidateStructureValidatorIT {

	private static final String CUSTOMER_SEMANTIC_CANDIDATE_FIXTURE =
			"classpath:fixtures/documentation-agent/customer-pass-semantic-candidate.json";

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private DocumentationProfileLoader profileLoader;

	@Autowired
	private DocumentationCandidateStructureValidator validator;

	@Test
	void acceptsTheFrozenCustomerPassSemanticCandidateFixture() {
		DocumentationProfile customerHandover = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		String candidateJson = readClasspath(CUSTOMER_SEMANTIC_CANDIDATE_FIXTURE);

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, customerHandover);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsASchemaInvalidCandidateBeforeAttemptingAnyDeeperCheck() {
		DocumentationProfile customerHandover = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		String candidateJson =
				"""
				{
				  "documents": []
				}
				""";

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, customerHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
		assertThat(result.issues()).allMatch(issue -> issue.validator().equals("schema"));
	}

	@Test
	void rejectsACandidateMissingTheRequiredDocumentType() {
		DocumentationProfile technicalHandover = profileLoader.resolve("TECHNICAL_HANDOVER@1.0.0");
		String candidateJson =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [
				    {
				      "documentType": "CUSTOMER_WEBSITE_HANDOVER",
				      "sections": [
				        {
				          "sectionType": "WEBSITE_OVERVIEW",
				          "blocks": []
				        }
				      ]
				    }
				  ]
				}
				""";

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, technicalHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.validator().equals("identity"));
	}

	@Test
	void rejectsACandidateWithTwoDocumentsOfTheRequiredType() {
		DocumentationProfile customerHandover = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		String minimalDocument = minimalCustomerDocumentJson();
		String candidateJson = "{\"schemaVersion\":\"1.0.0\",\"documents\":[" + minimalDocument + "," + minimalDocument + "]}";

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, customerHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(
				issue -> issue.validator().equals("identity") && issue.reason().contains("found 2"));
	}

	@Test
	void rejectsACandidateMissingARequiredSection() {
		DocumentationProfile customerHandover = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		// customer-handover.yaml requires WEBSITE_STRUCTURE, FEATURES etc. too - only WEBSITE_OVERVIEW is present here.
		String candidateJson =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [
				    {
				      "documentType": "CUSTOMER_WEBSITE_HANDOVER",
				      "sections": [
				        {
				          "sectionType": "WEBSITE_OVERVIEW",
				          "blocks": [
				            {"blockType": "NARRATIVE", "claims": [
				              {"claimKey": "C-1", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "x", "authorityKeys": ["AUTH-1"]}
				            ]}
				          ]
				        }
				      ]
				    }
				  ]
				}
				""";

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, customerHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(
				issue -> issue.validator().equals("structure") && issue.reason().contains("missing required section 'WEBSITE_STRUCTURE'"));
	}

	@Test
	void rejectsACandidateSectionTypeTheProfileDoesNotDeclare() {
		DocumentationProfile customerHandover = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		// ROUTING is a real sectionType (technical-handover-only), never declared by CUSTOMER_HANDOVER.
		String candidateJson =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [
				    {
				      "documentType": "CUSTOMER_WEBSITE_HANDOVER",
				      "sections": [
				        {
				          "sectionType": "ROUTING",
				          "blocks": []
				        }
				      ]
				    }
				  ]
				}
				""";

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, customerHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(
				issue -> issue.validator().equals("structure") && issue.reason().contains("unsupported section type 'ROUTING'"));
	}

	@Test
	void rejectsAnEmptyBlocksSectionWhenTheProfileRequiresAtLeastOneBlock() {
		DocumentationProfile customerHandover = profileLoader.resolve("CUSTOMER_HANDOVER@1.0.0");
		// WEBSITE_OVERVIEW has allowEmptyAgentBlocks:false in customer-handover.yaml.
		String candidateJson =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "documents": [
				    {
				      "documentType": "CUSTOMER_WEBSITE_HANDOVER",
				      "sections": [
				        {"sectionType": "WEBSITE_OVERVIEW", "blocks": []},
				        {"sectionType": "WEBSITE_STRUCTURE", "blocks": [
				          {"blockType": "NARRATIVE", "claims": [
				            {"claimKey": "C-1", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "x", "authorityKeys": ["AUTH-1"]}
				          ]}
				        ]},
				        {"sectionType": "FEATURES", "blocks": [
				          {"blockType": "NARRATIVE", "claims": [
				            {"claimKey": "C-2", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "x", "authorityKeys": ["AUTH-1"]}
				          ]}
				        ]},
				        {"sectionType": "CONTENT_AND_LANGUAGES", "blocks": [
				          {"blockType": "NARRATIVE", "claims": [
				            {"claimKey": "C-3", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "x", "authorityKeys": ["AUTH-1"]}
				          ]}
				        ]},
				        {"sectionType": "INTEGRATIONS", "blocks": [
				          {"blockType": "NARRATIVE", "claims": [
				            {"claimKey": "C-4", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "x", "authorityKeys": ["AUTH-1"]}
				          ]}
				        ]},
				        {"sectionType": "KNOWN_LIMITATIONS", "blocks": []},
				        {"sectionType": "PROJECT_STATUS", "blocks": [
				          {"blockType": "NARRATIVE", "claims": [
				            {"claimKey": "C-5", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "x", "authorityKeys": ["AUTH-1"]}
				          ]}
				        ]},
				        {"sectionType": "CHANGE_AND_MAINTENANCE", "blocks": []}
				      ]
				    }
				  ]
				}
				""";

		DocumentationCandidateValidationResult result = validator.validate(candidateJson, customerHandover);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(
				issue -> issue.validator().equals("structure") && issue.reason().contains("WEBSITE_OVERVIEW"));
		// KNOWN_LIMITATIONS/CHANGE_AND_MAINTENANCE both have allowEmptyAgentBlocks:true - their own empty blocks must not be flagged.
		assertThat(result.issues()).noneMatch(issue -> issue.reason().contains("KNOWN_LIMITATIONS") || issue.reason().contains("CHANGE_AND_MAINTENANCE"));
	}

	private String minimalCustomerDocumentJson() {
		return """
				{
				  "documentType": "CUSTOMER_WEBSITE_HANDOVER",
				  "sections": [
				    {"sectionType": "WEBSITE_OVERVIEW", "blocks": [
				      {"blockType": "NARRATIVE", "claims": [
				        {"claimKey": "C-1", "claimType": "CUSTOMER_FACT", "derivation": "DIRECT", "text": "x", "authorityKeys": ["AUTH-1"]}
				      ]}
				    ]}
				  ]
				}
				""";
	}

	private String readClasspath(String location) {
		try {
			return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
