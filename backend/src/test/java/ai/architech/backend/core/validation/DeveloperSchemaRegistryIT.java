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
 * Proves AIW-133's actual point: the Website Developer Agent V1 schema family genuinely composes
 * by {@code $ref} across files (unlike anything {@link ArtifactSchemaValidator} validates), and
 * that composition resolves correctly end to end - including through the pre-existing
 * Requirements/Designer schemas, which only became cross-referenceable once AIW-133 gave them
 * real {@code urn:aiw:schema:*:v1} {@code $id}s.
 */
@SpringBootTest
class DeveloperSchemaRegistryIT {

	private static final String DESIGN_PROPOSAL_SET_FIXTURE =
			"classpath:fixtures/designer-agent/valid-design-proposal-set.json";
	private static final String READY_RESULT_FIXTURE =
			"classpath:fixtures/developer-agent/schema-valid-ready-result.json";
	private static final String BLOCKED_RESULT_FIXTURE =
			"classpath:fixtures/developer-agent/schema-valid-blocked-result.json";

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private DeveloperSchemaRegistry registry;

	@Test
	void acceptsTheFrozenImplementationReadyResultFixture() {
		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:developer-agent-result:v1", readClasspath(READY_RESULT_FIXTURE));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void acceptsTheFrozenBlockedResultFixture() {
		SchemaValidationResult result =
				registry.validate("urn:aiw:schema:developer-agent-result:v1", readClasspath(BLOCKED_RESULT_FIXTURE));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsImplementationBlockedAsAFunctionalBindingStatus() {
		String json =
				"""
				{
				  "requirementRef": "req-content-1",
				  "designLocalRefs": ["sec-a-menu"],
				  "status": "IMPLEMENTATION_BLOCKED"
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:functional-binding:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void acceptsEachRealFunctionalBindingStatus() {
		String implementedLocal =
				"""
				{"requirementRef": "req-content-1", "designLocalRefs": ["sec-a-menu"], "status": "IMPLEMENTED_LOCAL"}
				""";
		String implementedBound =
				"""
				{
				  "requirementRef": "req-content-1",
				  "designLocalRefs": ["sec-a-menu"],
				  "status": "IMPLEMENTED_BOUND",
				  "integrationContractRef": "integration-fixture-1"
				}
				""";
		String unbound =
				"""
				{
				  "requirementRef": "req-content-1",
				  "designLocalRefs": ["sec-a-menu"],
				  "status": "UNBOUND",
				  "blockerCode": "MISSING_INTEGRATION_CONTRACT"
				}
				""";

		assertThat(registry.validate("urn:aiw:schema:functional-binding:v1", implementedLocal).valid()).isTrue();
		assertThat(registry.validate("urn:aiw:schema:functional-binding:v1", implementedBound).valid()).isTrue();
		assertThat(registry.validate("urn:aiw:schema:functional-binding:v1", unbound).valid()).isTrue();
	}

	@Test
	void acceptsAMinimalValidCandidate() {
		String json =
				"""
				{
				  "sourceDesign": {
				    "designArtifactVersionRef": "design-artifact-version-fixture",
				    "proposalLocalRef": "prop-a"
				  },
				  "runtimeProfileRef": "website-react-typescript-vite-client-v1",
				  "repositoryStateRef": "repo-state-fixture",
				  "implementationSummary": "Implemented the target proposal as a React/TypeScript/Vite website.",
				  "implementationAnchors": [
				    {
				      "designLocalRef": "page-a-home",
				      "kind": "PAGE",
				      "targets": [{"path": "src/pages/HomePage.tsx", "symbol": "HomePage"}]
				    }
				  ],
				  "functionalBindings": [],
				  "unresolvedIssues": []
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-implementation-candidate:v1", json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsACandidateMissingRepositoryStateRef() {
		String json =
				"""
				{
				  "sourceDesign": {
				    "designArtifactVersionRef": "design-artifact-version-fixture",
				    "proposalLocalRef": "prop-a"
				  },
				  "runtimeProfileRef": "website-react-typescript-vite-client-v1",
				  "implementationSummary": "Missing repositoryStateRef.",
				  "implementationAnchors": [
				    {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/HomePage.tsx"}]}
				  ],
				  "functionalBindings": [],
				  "unresolvedIssues": []
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-implementation-candidate:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void rejectsADeveloperBlockerFieldOnACandidate() {
		String json =
				"""
				{
				  "sourceDesign": {
				    "designArtifactVersionRef": "design-artifact-version-fixture",
				    "proposalLocalRef": "prop-a"
				  },
				  "runtimeProfileRef": "website-react-typescript-vite-client-v1",
				  "repositoryStateRef": "repo-state-fixture",
				  "implementationSummary": "A Candidate can never carry a DeveloperBlocker.",
				  "implementationAnchors": [
				    {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/HomePage.tsx"}]}
				  ],
				  "functionalBindings": [],
				  "unresolvedIssues": [],
				  "blockers": [
				    {
				      "code": "UPSTREAM_CONFLICT",
				      "relatedRequirementRefs": [],
				      "relatedDesignLocalRefs": [],
				      "relatedIntegrationContractRefs": [],
				      "diagnosticSummary": "Should never be accepted here."
				    }
				  ]
				}
				""";

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-implementation-candidate:v1", json);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void acceptsASingleProposalThroughTheWebsiteDesignProposalUrn() {
		String proposalSet = readClasspath(DESIGN_PROPOSAL_SET_FIXTURE);
		String singleProposal = extractFirstProposal(proposalSet);

		SchemaValidationResult result = registry.validate("urn:aiw:schema:website-design-proposal:v1", singleProposal);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void acceptsADeveloperExecutionInputComposingUpstreamCanonicalSchemas() {
		String customerProfile =
				"""
				{
				  "business": {"name": "Green Leaf Cafe"},
				  "contact": {"phone": "+43 1 2345678"},
				  "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}],
				  "offerings": [{"localRef": "cust-off-1", "name": "Coffee"}],
				  "openingHours": [],
				  "socialLinks": [],
				  "providedClaims": [],
				  "unknowns": [],
				  "conflicts": [],
				  "provenance": []
				}
				""";
		String websiteRequirements =
				"""
				{
				  "goals": [
				    {
				      "localRef": "req-goal-1",
				      "description": "Grow local visibility",
				      "strength": "must",
				      "sourceRefs": ["source-fixture-1"]
				    }
				  ],
				  "targetAudiences": [],
				  "contentRequirements": [
				    {
				      "localRef": "req-content-1",
				      "type": "offering",
				      "description": "Show the menu",
				      "strength": "must",
				      "sourceRefs": ["source-fixture-1"]
				    }
				  ],
				  "functionalRequirements": [],
				  "languages": [],
				  "constraints": [],
				  "unknowns": [],
				  "conflicts": []
				}
				""";
		String proposal =
				"""
				{
				  "localRef": "prop-a",
				  "name": "Warm Minimal",
				  "concept": "A calm, minimal layout emphasizing the menu.",
				  "websitePlan": {
				    "requirementRefs": ["req-goal-1"],
				    "pages": [
				      {
				        "localRef": "page-a-home",
				        "name": "Home",
				        "route": "/",
				        "purpose": "Introduce the cafe and lead to the menu",
				        "requirementRefs": ["req-goal-1", "req-content-1"],
				        "sections": [
				          {
				            "localRef": "sec-a-hero",
				            "kind": "hero",
				            "purpose": "Welcome visitors",
				            "layoutIntent": "centered, single column",
				            "customerDataRefs": ["cust-loc-1"],
				            "elements": [
				              {"localRef": "el-a-heading", "kind": "heading", "role": "title", "contentIntent": "Welcome message"}
				            ]
				          }
				        ]
				      }
				    ]
				  },
				  "designSpecification": {
				    "colors": [{"role": "primary", "value": "#2f4f2f"}],
				    "typography": [
				      {"role": "heading", "fontFamily": "Fraunces", "fontWeight": 600, "fontSizeRem": 2.2, "lineHeight": 1.2}
				    ],
				    "spacing": [{"role": "section", "valueRem": 3}],
				    "layout": {
				      "contentWidth": "narrow",
				      "density": "spacious",
				      "pageGutterRem": 1.5,
				      "sectionGapRem": 3,
				      "gridIntent": "single column"
				    },
				    "uiPatterns": [],
				    "imagery": {"direction": "warm, natural tones", "treatment": "soft-edged photography"},
				    "responsive": {
				      "navigationBehavior": "collapse into a menu icon",
				      "contentStacking": "vertical",
				      "typeScaling": "fluid clamp()",
				      "spacingAdjustment": "reduce by a third",
				      "mediaBehavior": "scale to container"
				    }
				  }
				}
				""";

		String input =
				"""
				{
				  "projectContext": {
				    "projectRef": "project-fixture-1",
				    "projectType": "WEBSITE",
				    "operation": "INITIAL_GENERATION"
				  },
				  "canonicalUpstream": {
				    "customerProfileArtifactVersionRef": "customer-profile-version-fixture",
				    "customerProfile": %s,
				    "websiteRequirementsArtifactVersionRef": "website-requirements-version-fixture",
				    "websiteRequirements": %s
				  },
				  "targetDesign": {
				    "designArtifactVersionRef": "design-artifact-version-fixture",
				    "targetProposalLocalRef": "prop-a",
				    "proposal": %s
				  },
				  "technicalContext": {
				    "runtimeProfileRef": "website-react-typescript-vite-client-v1",
				    "developmentBaseRef": "dev-base-fixture",
				    "dependencyPolicyRef": "website-developer-dependency-policy-v1",
				    "verificationPolicyRef": "website-developer-verification-policy-v1",
				    "toolCapabilityProfileRef": "website-developer-tools-v1"
				  },
				  "integrationContext": {
				    "integrationContracts": []
				  },
				  "executionContext": {
				    "agentContractVersion": "1.0.0",
				    "rulesetVersion": "1.0.0",
				    "skillProfileRef": "website-developer-skill-profile-v1",
				    "correctionBudget": {"maxCorrectionCycles": 3}
				  }
				}
				"""
						.formatted(customerProfile, websiteRequirements, proposal);

		SchemaValidationResult result = registry.validate("urn:aiw:schema:developer-execution-input:v1", input);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	private String extractFirstProposal(String proposalSetJson) {
		int proposalsIndex = proposalSetJson.indexOf("\"proposals\"");
		int arrayStart = proposalSetJson.indexOf('[', proposalsIndex);
		int depth = 0;
		int objectStart = -1;
		for (int i = arrayStart; i < proposalSetJson.length(); i++) {
			char c = proposalSetJson.charAt(i);
			if (c == '{') {
				if (depth == 0) {
					objectStart = i;
				}
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return proposalSetJson.substring(objectStart, i + 1);
				}
			}
		}
		throw new IllegalStateException("No proposal object found in fixture");
	}

	private String readClasspath(String location) {
		try {
			return resourceLoader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
