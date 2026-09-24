package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiRequest;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.ai.CostCalculator;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.profiles.DocumentSectionSpec;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.profiles.DocumentationProfileType;
import ai.architech.backend.core.documentation.profiles.DocumentationQaPreconditions;
import ai.architech.backend.core.documentation.profiles.SemanticDocumentSpec;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the deterministic surrounding code (counterfact selection, disclosure-severity
 * resolution, prompt assembly, response parsing) against a mocked {@link AiGateway}/{@link
 * CandidateFindingRepository} - no test here makes a real network call, matching
 * [[architech_cost_conscious_testing]]. Mirrors {@link DesignProposalSetSemanticReviewerTests}'
 * own pure-Mockito idiom for a standalone platform review step.
 */
@ExtendWith(MockitoExtension.class)
class DocumentationFactualConsistencyValidatorTests {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final UUID CANDIDATE_ID = UUID.randomUUID();
	private static final UUID QA_RESULT_ID = UUID.randomUUID();
	private static final UUID FINDING_ID = UUID.randomUUID();

	@Mock
	private AiGateway aiGateway;

	@Mock
	private CandidateFindingRepository candidateFindingRepository;

	@Mock
	private CostCalculator costCalculator;

	private DocumentationFactualConsistencyValidator newValidator() {
		return new DocumentationFactualConsistencyValidator(aiGateway, costCalculator, candidateFindingRepository, MAPPER);
	}

	@Test
	void returnsNoIssuesWhenTheModelSupportsEveryClaim() {
		DocumentationFactualConsistencyValidator validator = newValidator();
		mockAiResponse("""
				{"results": [{"claimKey": "C1", "outcome": "SUPPORTED"}]}
				""");

		List<JsonNode> issues = validator.validate(oneClaimCandidate("C1"), context(contextWithOneBindingFact()), technicalProfile());

		assertThat(issues).isEmpty();
	}

	@Test
	void returnsASchemaShapedEvaluationIssueForAnUnsupportedClaim() {
		DocumentationFactualConsistencyValidator validator = newValidator();
		mockAiResponse(
				"""
				{"results": [{"claimKey": "C1", "outcome": "UNSUPPORTED", "code": "CERTAINTY_UPGRADE", "reason": "claim overstates certainty"}]}
				""");

		List<JsonNode> issues = validator.validate(oneClaimCandidate("C1"), context(contextWithOneBindingFact()), technicalProfile());

		assertThat(issues).hasSize(1);
		JsonNode issue = issues.get(0);
		assertThat(issue.path("issueClass").asString()).isEqualTo("EVALUATION_ISSUE");
		assertThat(issue.path("issueCode").asString()).isEqualTo("CERTAINTY_UPGRADE");
		assertThat(issue.path("candidateClaimKey").asString()).isEqualTo("C1");
		assertThat(issue.path("blocking").asBoolean()).isTrue();
		assertThat(issue.path("remediationTarget").asString()).isEqualTo("AGENT");
	}

	@Test
	void treatsAMissingResultEntryAsAnInvalidValidatorOutputIssueRatherThanSilentlyDroppingIt() {
		DocumentationFactualConsistencyValidator validator = newValidator();
		mockAiResponse("""
				{"results": []}
				""");

		List<JsonNode> issues = validator.validate(oneClaimCandidate("C1"), context(contextWithOneBindingFact()), technicalProfile());

		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).path("issueCode").asString()).isEqualTo("INVALID_VALIDATOR_OUTPUT");
		assertThat(issues.get(0).path("issueClass").asString()).isEqualTo("SYSTEM_ISSUE");
	}

	@Test
	void treatsAnUnsupportedOutcomeWithoutARealCodeAsInvalidValidatorOutput() {
		DocumentationFactualConsistencyValidator validator = newValidator();
		mockAiResponse(
				"""
				{"results": [{"claimKey": "C1", "outcome": "UNSUPPORTED", "code": "MADE_UP_CODE", "reason": "x"}]}
				""");

		List<JsonNode> issues = validator.validate(oneClaimCandidate("C1"), context(contextWithOneBindingFact()), technicalProfile());

		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).path("issueCode").asString()).isEqualTo("INVALID_VALIDATOR_OUTPUT");
	}

	@Test
	void treatsUnparseableModelOutputAsInvalidValidatorOutputForEveryClaimSent() {
		DocumentationFactualConsistencyValidator validator = newValidator();
		mockAiResponse("not json {{{");

		List<JsonNode> issues = validator.validate(oneClaimCandidate("C1"), context(contextWithOneBindingFact()), technicalProfile());

		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).path("issueCode").asString()).isEqualTo("INVALID_VALIDATOR_OUTPUT");
	}

	@Test
	void includesAnUnboundSiblingFunctionalBindingFactAsACounterfactForABoundClaim() {
		DocumentationFactualConsistencyValidator validator = newValidator();
		mockAiResponse("""
				{"results": [{"claimKey": "C1", "outcome": "SUPPORTED"}]}
				""");

		validator.validate(oneClaimCandidate("C1"), context(contextWithOneBindingFact()), technicalProfile());

		ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
		verify(aiGateway).invoke(captor.capture());
		String userMessage = captor.getValue().messages().stream()
				.filter(m -> "user".equals(m.role()))
				.map(m -> m.content())
				.findFirst()
				.orElseThrow();
		assertThat(userMessage).contains("F_UNBOUND_SIBLING").contains("UNBOUND");
	}

	@Test
	void resolvesARealDisclosedFindingsSeverityAndBlocksOnAnUnresolvableDisclosureKey() {
		DocumentationFactualConsistencyValidator validator = newValidator();
		CandidateFinding finding = new CandidateFinding(
				QA_RESULT_ID, UUID.randomUUID(), CANDIDATE_ID, "SOME_CODE", "QA_FINDING", "MAJOR", "[]", "summary", null, null, "[]",
				"fp-1", "{}");
		when(candidateFindingRepository.findById(FINDING_ID)).thenReturn(Optional.of(finding));
		mockAiResponse("""
				{"results": [{"claimKey": "C1", "outcome": "SUPPORTED"}]}
				""");

		validator.validate(oneClaimWithDisclosureCandidate(), context(contextWithOneDisclosedFinding()), customerProfile());

		ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
		verify(aiGateway).invoke(captor.capture());
		String userMessage = captor.getValue().messages().stream()
				.filter(m -> "user".equals(m.role()))
				.map(m -> m.content())
				.findFirst()
				.orElseThrow();
		assertThat(userMessage).contains("\"disclosureSeverity\":\"MAJOR\"");
	}

	@Test
	void blocksWithoutCallingTheModelWhenADisclosureKeyCannotBeResolvedToARealFinding() {
		DocumentationFactualConsistencyValidator validator = newValidator();

		List<JsonNode> issues = validator.validate(
				oneClaimWithDisclosureCandidate(), context(contextWithNoMatchingDisclosureEntry()), customerProfile());

		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).path("issueCode").asString()).isEqualTo("INVALID_VALIDATOR_OUTPUT");
	}

	// -- fixtures --

	private void mockAiResponse(String content) {
		when(aiGateway.invoke(any())).thenReturn(new AiResponse("mock", "mock-model", content, "corr-1", null, null, null, null));
	}

	private String oneClaimCandidate(String claimKey) {
		return """
				{
				  "documents": [
				    {"documentType": "TECHNICAL_HANDOVER_GUIDE", "sections": [
				      {"sectionType": "FUNCTIONAL_BEHAVIOR", "blocks": [
				        {"blockType": "NARRATIVE", "claims": [
				          {"claimKey": "%s", "claimType": "FUNCTIONAL_BEHAVIOR", "derivation": "DIRECT", "text": "The contact form is bound.", "authorityKeys": ["AUTH_BINDING_BOUND"]}
				        ]}
				      ]}
				    ]}
				  ]
				}
				""".formatted(claimKey);
	}

	private String oneClaimWithDisclosureCandidate() {
		return """
				{
				  "documents": [
				    {"documentType": "CUSTOMER_WEBSITE_HANDOVER", "sections": [
				      {"sectionType": "KNOWN_LIMITATIONS", "blocks": [
				        {"blockType": "NARRATIVE", "claims": [
				          {"claimKey": "C1", "claimType": "KNOWN_LIMITATION", "derivation": "DIRECT", "text": "There is a minor known issue.", "authorityKeys": ["AUTH_FINDING_1"], "disclosureKeys": ["DISC_1"]}
				        ]}
				      ]}
				    ]}
				  ]
				}
				""";
	}

	private String contextWithOneBindingFact() {
		return """
				{
				  "primaryAudience": "DEVELOPER",
				  "targetLocale": "en-GB",
				  "authorityCatalog": [
				    {"key": "AUTH_BINDING_BOUND", "authorityRef": {"authorityDomain": "FUNCTIONAL_BINDING", "artifactType": "WEBSITE_IMPLEMENTATION_CANDIDATE", "artifactVersionRef": "C4", "locator": {"kind": "OBJECT_ID", "value": "REQ_1"}}, "safeFactKeys": ["F_BOUND"]}
				  ],
				  "resolvedFacts": [
				    {"factKey": "F_BOUND", "authorityKey": "AUTH_BINDING_BOUND", "factDomain": "FUNCTIONAL_BINDING", "factType": "BINDING_STATE", "state": "KNOWN", "classification": "PUBLIC_DOCUMENTABLE", "value": {"valueType": "STRING", "value": "IMPLEMENTED_BOUND"}},
				    {"factKey": "F_UNBOUND_SIBLING", "authorityKey": "AUTH_BINDING_OTHER", "factDomain": "FUNCTIONAL_BINDING", "factType": "BINDING_STATE", "state": "KNOWN", "classification": "PUBLIC_DOCUMENTABLE", "value": {"valueType": "STRING", "value": "UNBOUND"}},
				    {"factKey": "F_UNRELATED", "authorityKey": "AUTH_OTHER", "factDomain": "CUSTOMER_FACT", "factType": "BUSINESS_NAME", "state": "KNOWN", "classification": "PUBLIC_DOCUMENTABLE", "value": {"valueType": "STRING", "value": "Beispiel GmbH"}}
				  ],
				  "findingDisclosureView": {"entries": []}
				}
				""";
	}

	private String contextWithOneDisclosedFinding() {
		return """
				{
				  "primaryAudience": "CUSTOMER",
				  "targetLocale": "de-AT",
				  "authorityCatalog": [
				    {"key": "AUTH_FINDING_1", "authorityRef": {"authorityDomain": "QA_FINDING", "artifactType": "QA_RESULT", "artifactVersionRef": "%s", "locator": {"kind": "OBJECT_ID", "value": "%s"}}, "safeFactKeys": []}
				  ],
				  "resolvedFacts": [],
				  "findingDisclosureView": {"entries": [
				    {"disclosureKey": "DISC_1", "findingAuthorityKey": "AUTH_FINDING_1", "action": "DISCLOSE", "audience": "CUSTOMER", "reasonCode": "MATERIAL_CUSTOMER_IMPACT", "materialImpactFactKeys": [], "currentCandidateRef": "%s"}
				  ]}
				}
				""".formatted(QA_RESULT_ID, FINDING_ID, CANDIDATE_ID);
	}

	private String contextWithNoMatchingDisclosureEntry() {
		return """
				{
				  "primaryAudience": "CUSTOMER",
				  "targetLocale": "de-AT",
				  "authorityCatalog": [],
				  "resolvedFacts": [],
				  "findingDisclosureView": {"entries": []}
				}
				""";
	}

	private DocumentationContext context(String contentJson) {
		return new DocumentationContext(UUID.randomUUID(), UUID.randomUUID(), CANDIDATE_ID, QA_RESULT_ID, "TECHNICAL_HANDOVER@1.0.0", 1, contentJson);
	}

	private DocumentationProfile technicalProfile() {
		return new DocumentationProfile(
				"TECHNICAL_HANDOVER@1.0.0",
				DocumentationProfileType.TECHNICAL_HANDOVER,
				true,
				"DEVELOPER",
				"GENERATE_DOCUMENTATION",
				List.of(),
				List.of(),
				new DocumentationQaPreconditions("FULL_RELEASE", List.of("PASS", "HOLD")),
				Optional.empty(),
				List.of(new SemanticDocumentSpec(
						"TECHNICAL_HANDOVER_GUIDE", true, List.of(new DocumentSectionSpec("FUNCTIONAL_BEHAVIOR", true, false)))),
				List.of(),
				Map.of(),
				List.of("FUNCTIONAL_BEHAVIOR"),
				"DOCUMENTATION_SKILL_SET@1.0.0",
				"technical-handover-composition",
				true,
				List.of("de-AT", "en-GB"),
				"DOCUMENTATION_POLICY@1.0.0");
	}

	private DocumentationProfile customerProfile() {
		return new DocumentationProfile(
				"CUSTOMER_HANDOVER@1.0.0",
				DocumentationProfileType.CUSTOMER_HANDOVER,
				true,
				"CUSTOMER",
				"GENERATE_DOCUMENTATION",
				List.of(),
				List.of(),
				new DocumentationQaPreconditions("FULL_RELEASE", List.of("PASS")),
				Optional.empty(),
				List.of(new SemanticDocumentSpec(
						"CUSTOMER_WEBSITE_HANDOVER", true, List.of(new DocumentSectionSpec("KNOWN_LIMITATIONS", true, true)))),
				List.of(),
				Map.of(),
				List.of("KNOWN_LIMITATION"),
				"DOCUMENTATION_SKILL_SET@1.0.0",
				"customer-handover-composition",
				true,
				List.of("de-AT", "en-GB"),
				"DOCUMENTATION_POLICY@1.0.0");
	}
}
