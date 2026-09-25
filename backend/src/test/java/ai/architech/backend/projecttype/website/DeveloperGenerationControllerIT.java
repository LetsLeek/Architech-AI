package ai.architech.backend.projecttype.website;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * MockMvc proof of AIW-212's own trigger endpoint, mirroring {@code
 * DesignProposalGenerationControllerIT}'s own pattern. Uses the same direct {@link
 * ArtifactRepository}/{@link ArtifactVersionRepository} seeding {@code
 * InitialGenerationOrchestratorIT}/{@code DeveloperToolLoopOrchestratorIT} already use for this
 * exact package's own canonical artifacts, rather than {@code CandidatePromoter} - that mechanism
 * is specific to M2's own Design Proposal promotion flow, not a general precondition of this test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DefaultApiKeyHeaderConfig.class) // AIW-185: attaches a valid X-API-Key to every MockMvc request by default
@Transactional
class DeveloperGenerationControllerIT {

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

	private UUID designProposalSetVersionId;

	@AfterEach
	void resetMockScripts() {
		mockAiProvider.resetScripts();
	}

	@Test
	void rejectsStartingForAnUnknownProject() throws Exception {
		mockMvc.perform(post("/api/projects/{id}/website-generation", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void rejectsStartingWhenNoCanonicalDesignProposalSetExistsYet() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(post("/api/projects/{id}/website-generation", project.getId())).andExpect(status().isNotFound());
	}

	@Test
	void rejectsStartingWhenAGenerationIsAlreadyRunningForTheProject() throws Exception {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		AgentExecution running = new AgentExecution(projectId, "developer-agent", 1);
		running.start();
		agentExecutionRepository.saveAndFlush(running);

		mockMvc.perform(post("/api/projects/{id}/website-generation", projectId)).andExpect(status().isConflict());
	}

	@Test
	void startsGenerationAndReturnsAPerSiblingAggregateResult() throws Exception {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");

		List<AiResponse> script = new ArrayList<>();
		for (String localRef : List.of("prop-a", "prop-b", "prop-c")) {
			script.add(toolUseWrite());
			script.add(finalAnswerResponse(readyResultEnvelope(localRef)));
			// AIW-213: driveSibling now triggers QA (a real AiGateway call, same shared "mock"
			// provider) the moment a sibling's own Candidate exists - one throwaway entry keeps the
			// next sibling's own two scripted entries correctly positioned. Its own content doesn't
			// matter: QA failing on unscripted/invalid output is already an expected, non-fatal path.
			script.add(finalAnswerResponse(""));
		}
		mockAiProvider.script(script);

		mockMvc.perform(post("/api/projects/{id}/website-generation", projectId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.allSucceeded").value(true))
				.andExpect(jsonPath("$.siblings.length()").value(3))
				.andExpect(jsonPath("$.siblings[0].proposalLocalRef").value("prop-a"))
				.andExpect(jsonPath("$.siblings[0].status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.siblings[0].candidateId").exists())
				.andExpect(jsonPath("$.siblings[1].proposalLocalRef").value("prop-b"))
				.andExpect(jsonPath("$.siblings[1].status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.siblings[2].proposalLocalRef").value("prop-c"))
				.andExpect(jsonPath("$.siblings[2].status").value("SUCCEEDED"));
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
}
