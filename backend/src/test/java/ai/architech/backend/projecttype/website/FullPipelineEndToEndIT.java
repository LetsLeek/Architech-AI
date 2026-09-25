package ai.architech.backend.projecttype.website;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiMessage;
import ai.architech.backend.core.ai.AiRequest;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.ai.ContentBlock;
import ai.architech.backend.core.ai.MockAiProvider;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.security.DefaultApiKeyHeaderConfig;
import ai.architech.backend.core.verification.AuthoritativeRunnerVerifier;
import ai.architech.backend.core.verification.GateResult;
import ai.architech.backend.core.verification.RunnerVerificationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AIW-211's own real, concrete proof for AIW-207 (Product End-to-End Enablement): every prior
 * ticket in this epic (AIW-208 through AIW-213) wired one real piece of the Developer &rarr; QA
 * &rarr; Documentation chain and was individually tested in isolation, but nothing before this
 * class ever proved the *whole* chain succeeds end-to-end through the real REST API surface a
 * frontend would actually call - {@link DeveloperGenerationControllerIT}'s own passing test
 * deliberately scripts QA's own AI call with an invalid/unscripted final answer per sibling (see
 * its own comment), so QA never PASSes there and Documentation is never triggered.
 *
 * <p>This test drives the real {@code POST /website-generation} endpoint (mirroring {@link
 * DeveloperGenerationControllerIT}'s exact seeding/setup), then the real {@code GET /qa-results}
 * and {@code GET /documentation-packages} endpoints, and asserts a genuine, persisted QA {@code
 * PASS} and a genuine, persisted Documentation package version are both visible through them.
 *
 * <p><b>Only one sibling (prop-a) is driven all the way to a QA PASS and a Documentation
 * package</b> - proving the chain once is enough; the other two siblings (prop-b, prop-c) keep
 * {@code DeveloperGenerationControllerIT}'s own "QA failing on unscripted/invalid output is
 * already an expected, non-fatal path" shortcut, so their own QA calls never produce a persisted
 * {@code QaResult} at all (see {@code QaTriggerService#triggerFullReleaseQa} - a non-validating
 * QA run returns before ever calling {@code QaResultAssemblyService#assemble}). That keeps {@code
 * GET /qa-results} (which reports the single most recent {@code QaResult} across a project's
 * candidates) unambiguous without needing to special-case ordering.
 *
 * <p><b>How the QA/Documentation responses are produced without abandoning {@link
 * MockAiProvider}</b>: {@link MockAiProvider}'s own script queue is strictly static/FIFO (see its
 * own javadoc) and cannot echo request-specific content back - but the real {@code
 * QaExecutionInputAssembler} generates a fresh {@code qaExecutionRef}/{@code inputSnapshotRef} on
 * every call (by design), and {@code SemanticQaReviewOutputIdentityValidator} requires the QA
 * response to echo those exact values back. Rather than replace {@link MockAiProvider} entirely,
 * this test wraps the real {@link AiGateway} bean in a {@link MockitoSpyBean} spy (the same idiom
 * {@code QaTriggerServiceIT}/{@code DocumentationTriggerServiceIT} use for a full {@code
 * MockitoBean}, but a spy here so unstubbed calls still fall through to the real {@link AiGateway}
 * implementation) and stubs only the {@code qa-reasoning}/{@code documentation-reasoning}/{@code
 * documentation-factual-consistency} model profiles directly on it - the QA stub reads the actual
 * assembled {@code qa-execution-input} straight out of the captured {@link AiRequest}'s own prompt
 * and echoes the identity fields back, the same technique {@code QaTriggerServiceIT}'s own {@code
 * extractQaExecutionInput}/{@code envelopeFields} helpers use. Every other model profile (the
 * Website Developer Agent's own {@code implementation-reasoning} calls) is left unstubbed, so
 * those calls fall through to the real {@link AiGateway} exactly as before and are still scripted
 * through {@link MockAiProvider#script} exactly like {@link DeveloperGenerationControllerIT} - no
 * throwaway QA entry is needed in that script any more, since QA/Documentation calls never reach
 * {@link MockAiProvider} at all once stubbed on the spy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DefaultApiKeyHeaderConfig.class) // AIW-185: attaches a valid X-API-Key to every MockMvc request by default
@Transactional
class FullPipelineEndToEndIT {

	private static final String QA_MODEL_PROFILE = "qa-reasoning";
	private static final String DOCUMENTATION_GENERATION_MODEL_PROFILE = "documentation-reasoning";
	private static final String DOCUMENTATION_EVALUATION_MODEL_PROFILE = "documentation-factual-consistency";

	private static final String AUTH_IMPLEMENTATION_SUMMARY = "AUTH_IMPLEMENTATION_SUMMARY";
	private static final String AUTH_FULL_RELEASE_GATE = "AUTH_FULL_RELEASE_GATE";

	private static final String CUSTOMER_PROFILE =
			"""
			{"business": {"name": "Green Leaf Cafe"}, "contact": {"phone": "+43 1 2345678"},
			 "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}], "offerings": [],
			 "openingHours": [], "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": []}""";

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{"goals": [], "targetAudiences": [], "contentRequirements": [],
			 "functionalRequirements": [
			   {"localRef": "req-func-1", "type": "booking", "description": "Let customers book a table", "strength": "must", "sourceRefs": ["s1"]}
			 ],
			 "languages": [], "constraints": [], "unknowns": [], "conflicts": []}""";

	private static String proposal(String localRef) {
		return
				"""
				{"localRef": "%s", "name": "Warm Minimal", "concept": "A calm, minimal layout.",
				 "websitePlan": {"requirementRefs": ["req-func-1"], "pages": [
				   {"localRef": "page-a-home", "name": "Home", "route": "/", "purpose": "Introduce the cafe",
				    "sections": [
				      {"localRef": "sec-a-hero", "kind": "hero", "purpose": "Welcome visitors", "layoutIntent": "centered",
				       "elements": [{"localRef": "el-a-heading", "kind": "heading", "role": "title", "contentIntent": "Welcome"}]}
				    ]}
				 ]},
				 "designSpecification": {
				   "colors": [{"role": "primary", "value": "#2f4f2f"}],
				   "typography": [{"role": "heading", "fontFamily": "Fraunces", "fontWeight": 600, "fontSizeRem": 2.2, "lineHeight": 1.2}],
				   "spacing": [{"role": "section", "valueRem": 3}],
				   "layout": {"contentWidth": "narrow", "density": "spacious", "pageGutterRem": 1.5, "sectionGapRem": 3, "gridIntent": "single column"},
				   "uiPatterns": [], "imagery": {"direction": "warm", "treatment": "soft-edged"},
				   "responsive": {"navigationBehavior": "collapse", "contentStacking": "vertical", "typeScaling": "fluid",
				     "spacingAdjustment": "reduce", "mediaBehavior": "scale"}}}"""
						.formatted(localRef);
	}

	private static String proposalSet(String... localRefs) {
		StringBuilder sb = new StringBuilder("{\"proposals\": [");
		for (int i = 0; i < localRefs.length; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(proposal(localRefs[i]));
		}
		return sb.append("]}").toString();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private MockAiProvider mockAiProvider;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthoritativeRunnerVerifier authoritativeRunnerVerifier;

	@MockitoSpyBean
	private AiGateway aiGateway;

	private UUID designProposalSetVersionId;

	@AfterEach
	void resetMockScripts() {
		mockAiProvider.resetScripts();
	}

	@Test
	void developerGenerationRealQaPassAndRealDocumentationPackageAreAllVisibleThroughTheRealApi() throws Exception {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");

		stubPassingQaOnceThenThrowawayForTheRest();
		ObjectNode validTechnicalCandidate = buildFullValidTechnicalCandidate("Runtime detail claim text.");
		stubDocumentationGeneration(validTechnicalCandidate.toString());
		stubDocumentationEvaluation(validTechnicalCandidate);

		List<AiResponse> script = new ArrayList<>();
		for (String localRef : List.of("prop-a", "prop-b", "prop-c")) {
			script.add(toolUseWrite());
			script.add(finalAnswerResponse(readyResultEnvelope(localRef)));
		}
		mockAiProvider.script(script);

		mockMvc.perform(post("/api/projects/{id}/website-generation", projectId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.allSucceeded").value(true))
				.andExpect(jsonPath("$.siblings.length()").value(3))
				.andExpect(jsonPath("$.siblings[0].proposalLocalRef").value("prop-a"))
				.andExpect(jsonPath("$.siblings[0].status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.siblings[0].candidateId").exists());

		mockMvc.perform(get("/api/projects/{id}/qa-results", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gateOutcome").value("PASS"))
				.andExpect(jsonPath("$.qaResultId").exists())
				.andExpect(jsonPath("$.testedCandidateId").exists());

		mockMvc.perform(get("/api/projects/{id}/documentation-packages", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
				.andExpect(jsonPath("$[0].currentPackageVersion").exists())
				.andExpect(jsonPath("$[0].currentPackageVersion.packageVersionId").exists())
				.andExpect(jsonPath("$[0].currentPackageVersion.revision").value(1));
	}

	private RunnerVerificationResult allGatesPass() {
		return new RunnerVerificationResult(AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream().map(GateResult::pass).toList());
	}

	private AiResponse toolUseWrite() {
		ObjectNode writeInput = objectMapper.createObjectNode();
		writeInput.put("operation", "write").put("path", "src/pages/Home.tsx").put("content", "export const Home = () => null;");
		return new AiResponse(
				"mock", "mock-model", "", null, null, null, null, null, "tool_use",
				List.of(new ContentBlock.ToolUse("call-1", "filesystem", writeInput)));
	}

	private AiResponse finalAnswerResponse(String content) {
		return new AiResponse("mock", "mock-model", content, null, null, null, null, null);
	}

	private String readyResultEnvelope(String proposalLocalRef) {
		return
				"""
				{"developer-agent-result": {
				  "resultType": "IMPLEMENTATION_READY",
				  "targetDesign": {"designArtifactVersionRef": "%s", "proposalLocalRef": "%s"},
				  "implementationSummary": "Implemented the home page hero.",
				  "implementationAnchors": [
				    {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				    {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx", "symbol": "HeroSection"}]}
				  ],
				  "functionalBindings": [{"requirementRef": "req-func-1", "designLocalRefs": ["sec-a-hero"], "status": "IMPLEMENTED_LOCAL"}],
				  "unresolvedIssues": []
				}}
				"""
						.formatted(designProposalSetVersionId, proposalLocalRef);
	}

	private UUID seedProject(String... proposalLocalRefs) {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		designProposalSetVersionId = persistArtifactVersion(project.getId(), "design-proposal-set", proposalSet(proposalLocalRefs));
		return project.getId();
	}

	private UUID persistArtifactVersion(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		return artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), content)).getId();
	}

	// -- AiGateway spy stubbing for QA/Documentation (mirrors QaTriggerServiceIT/DocumentationTriggerServiceIT,
	// but on a spy rather than a full mock so the Developer Agent's own implementation-reasoning calls still
	// fall through to the real AiGateway -> MockAiProvider path scripted above) --

	// Note: stubbing goes through doAnswer/doReturn(...).when(spy)..., not when(spy...)...then...
	// - aiGateway is a MockitoSpyBean wrapping the real bean, and when(spy.invoke(argThat(...)))
	// would actually invoke the real method first (with the argThat matcher's own placeholder
	// null "argument") before Mockito ever gets to record the stub - the classic Mockito spy
	// gotcha, and exactly what NPE'd here the first time this was written with when(...).

	private void stubPassingQaOnceThenThrowawayForTheRest() {
		doAnswer(invocation -> {
					AiRequest request = invocation.getArgument(0);
					JsonNode input = extractQaExecutionInput(request);
					return mockResponse("{\"semantic-qa-review-output\": " + passingBody(input) + "}");
				})
				// prop-b/prop-c's own QA calls: unscripted/invalid content, the same non-fatal
				// shortcut DeveloperGenerationControllerIT's own comment documents - QA fails
				// validation for these two and never persists a QaResult at all.
				.doReturn(mockResponse(""))
				.when(aiGateway)
				.invoke(argThat(req -> req != null && QA_MODEL_PROFILE.equals(req.modelProfile())));
	}

	private void stubDocumentationGeneration(String candidateJson) {
		doReturn(mockResponse(candidateJson))
				.when(aiGateway)
				.invoke(argThat(req -> req != null && DOCUMENTATION_GENERATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	private void stubDocumentationEvaluation(ObjectNode candidate) {
		doReturn(mockResponse(evaluationResponseAllSupported(candidate)))
				.when(aiGateway)
				.invoke(argThat(req -> req != null && DOCUMENTATION_EVALUATION_MODEL_PROFILE.equals(req.modelProfile())));
	}

	private AiResponse mockResponse(String content) {
		return new AiResponse("mock", "mock-model", content, "correlation", null, null, null, null);
	}

	private JsonNode extractQaExecutionInput(AiRequest request) {
		String marker = "[qa-execution-input]\n";
		String userContent = request.messages().stream()
				.filter(m -> "user".equals(m.role()))
				.map(AiMessage::content)
				.findFirst()
				.orElseThrow();
		int idx = userContent.indexOf(marker);
		String json = userContent.substring(idx + marker.length()).trim();
		return objectMapper.readTree(json);
	}

	private String passingBody(JsonNode input) {
		ObjectNode body = envelopeFields(input);
		body.set("findingCandidates", objectMapper.createArrayNode());
		body.set("authorityIssueCandidates", objectMapper.createArrayNode());
		body.set("evaluationIssueCandidates", objectMapper.createArrayNode());
		body.set("semanticReviewCoverage", coverage());
		return body.toString();
	}

	private ObjectNode envelopeFields(JsonNode input) {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("schemaVersion", "1.0.0");
		body.put("qaExecutionRef", input.path("qaExecutionRef").asString());
		body.put("testedCandidateRef", input.path("target").path("candidateRef").asString());
		body.put("qaProfileRef", input.path("qaAuthority").path("qaProfileRef").asString());
		body.put("inputSnapshotRef", input.path("provenance").path("inputSnapshotRef").asString());
		return body;
	}

	private ArrayNode coverage() {
		ArrayNode coverage = objectMapper.createArrayNode();
		ObjectNode entry = objectMapper.createObjectNode();
		entry.put("reviewTaskRef", "review-navigation-1");
		entry.put("domain", "NAVIGATION");
		entry.put("status", "COMPLETED");
		ArrayNode evidenceRefs = objectMapper.createArrayNode();
		evidenceRefs.add("ev-1");
		entry.set("evidenceRefs", evidenceRefs);
		coverage.add(entry);
		return coverage;
	}

	// -- valid documentation-package-candidate fixture (TECHNICAL_HANDOVER - the profile
	// FULL_RELEASE_QA_FINALIZED resolves to, per DocumentationWorkflowTriggerEvaluator), copied
	// from DocumentationAgentV1EndToEndIT's own helpers --

	private String evaluationResponseAllSupported(ObjectNode candidate) {
		ArrayNode results = objectMapper.createArrayNode();
		for (String claimKey : collectClaimKeys(candidate)) {
			ObjectNode result = objectMapper.createObjectNode();
			result.put("claimKey", claimKey);
			result.put("outcome", "SUPPORTED");
			results.add(result);
		}
		ObjectNode root = objectMapper.createObjectNode();
		root.set("results", results);
		return root.toString();
	}

	private List<String> collectClaimKeys(ObjectNode candidate) {
		List<String> claimKeys = new ArrayList<>();
		for (var section : candidate.path("documents").get(0).path("sections")) {
			for (var block : section.path("blocks")) {
				if ("NARRATIVE".equals(block.path("blockType").asString(null))) {
					for (var claim : block.path("claims")) {
						claimKeys.add(claim.path("claimKey").asString());
					}
				} else if ("LIST".equals(block.path("blockType").asString(null))) {
					for (var item : block.path("items")) {
						for (var claim : item.path("claims")) {
							claimKeys.add(claim.path("claimKey").asString());
						}
					}
				}
			}
		}
		return claimKeys;
	}

	private ObjectNode buildFullValidTechnicalCandidate(String claimText) {
		ObjectNode document = objectMapper.createObjectNode();
		document.put("documentType", "TECHNICAL_HANDOVER_GUIDE");
		ArrayNode sections = objectMapper.createArrayNode();

		sections.add(sectionWithOneClaim(
				"IMPLEMENTATION_OVERVIEW", "T_IMPLEMENTATION_OVERVIEW", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim(
				"RUNTIME_AND_ARCHITECTURE",
				"T_RUNTIME_AND_ARCHITECTURE",
				"IMPLEMENTATION_DESCRIPTION",
				AUTH_IMPLEMENTATION_SUMMARY,
				claimText));
		sections.add(sectionWithOneClaim("ROUTING", "T_ROUTING", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim(
				"FUNCTIONAL_BEHAVIOR", "T_FUNCTIONAL_BEHAVIOR", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(
				sectionWithOneClaim("INTEGRATIONS", "T_INTEGRATIONS", "IMPLEMENTATION_DESCRIPTION", AUTH_IMPLEMENTATION_SUMMARY, claimText));
		sections.add(sectionWithOneClaim(
				"TECHNICAL_CONSTRAINTS", "T_TECHNICAL_CONSTRAINTS", "TECHNICAL_CONSTRAINT", AUTH_IMPLEMENTATION_SUMMARY, claimText));

		ObjectNode qaStatusClaim = claimNode("T_QA_STATUS", "QA_STATUS", List.of(AUTH_FULL_RELEASE_GATE), claimText);
		sections.add(sectionWithBlocks("QA_AND_OUTSTANDING_ISSUES", narrativeBlock(qaStatusClaim)));

		sections.add(emptySection("DEPLOYMENT_INFORMATION"));
		sections.add(emptySection("MAINTENANCE_NOTES"));
		sections.add(emptySection("REFERENCES"));

		document.set("sections", sections);

		ArrayNode documents = objectMapper.createArrayNode();
		documents.add(document);

		ObjectNode candidate = objectMapper.createObjectNode();
		candidate.put("schemaVersion", "1.0.0");
		candidate.set("documents", documents);
		return candidate;
	}

	private ObjectNode sectionWithOneClaim(String sectionType, String claimKey, String claimType, String authorityKey, String claimText) {
		ObjectNode claim = claimNode(claimKey, claimType, List.of(authorityKey), claimText);
		return sectionWithBlocks(sectionType, narrativeBlock(claim));
	}

	private ObjectNode sectionWithBlocks(String sectionType, ObjectNode... blocks) {
		ObjectNode section = objectMapper.createObjectNode();
		section.put("sectionType", sectionType);
		ArrayNode blocksArray = objectMapper.createArrayNode();
		for (ObjectNode block : blocks) {
			blocksArray.add(block);
		}
		section.set("blocks", blocksArray);
		return section;
	}

	private ObjectNode emptySection(String sectionType) {
		ObjectNode section = objectMapper.createObjectNode();
		section.put("sectionType", sectionType);
		section.set("blocks", objectMapper.createArrayNode());
		return section;
	}

	private ObjectNode narrativeBlock(ObjectNode... claims) {
		ObjectNode block = objectMapper.createObjectNode();
		block.put("blockType", "NARRATIVE");
		ArrayNode claimsArray = objectMapper.createArrayNode();
		for (ObjectNode claim : claims) {
			claimsArray.add(claim);
		}
		block.set("claims", claimsArray);
		return block;
	}

	private ObjectNode claimNode(String claimKey, String claimType, List<String> authorityKeys, String claimText) {
		ObjectNode claim = objectMapper.createObjectNode();
		claim.put("claimKey", claimKey);
		claim.put("claimType", claimType);
		claim.put("derivation", "DIRECT");
		claim.put("text", claimText);
		ArrayNode authorityKeysArray = objectMapper.createArrayNode();
		authorityKeys.forEach(authorityKeysArray::add);
		claim.set("authorityKeys", authorityKeysArray);
		return claim;
	}
}
