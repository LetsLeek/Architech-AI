package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.verification.AuthoritativeRunnerVerifier;
import ai.architech.backend.core.verification.GateResult;
import ai.architech.backend.core.verification.RunnerVerificationResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fixture-driven, zero-live-Anthropic-call proof of AIW-212's own "Track 2C" connective layer:
 * all three A/B/C siblings actually driven to a terminal status, not just fanned out and left at
 * {@code RUNNING} the way {@code InitialGenerationOrchestratorIT} itself only proves. Mirrors
 * {@code DeveloperToolLoopOrchestratorIT}'s own mocking posture ({@link MockAiProvider}, a stubbed
 * {@link AuthoritativeRunnerVerifier}) composed with {@code InitialGenerationOrchestratorIT}'s own
 * real-provisioning seeding shape (a project with exactly three canonical design proposals).
 */
@SpringBootTest
@Transactional
class WebsiteGenerationDrivingServiceIT {

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
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private WebsiteGenerationDrivingService drivingService;

	@Autowired
	private MockAiProvider mockAiProvider;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthoritativeRunnerVerifier authoritativeRunnerVerifier;

	@AfterEach
	void resetMockScripts() {
		mockAiProvider.resetScripts();
	}

	@Test
	void drivesAllThreeSiblingsToAnAcceptedCandidateEach(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");

		List<AiResponse> script = new ArrayList<>();
		for (String localRef : List.of("prop-a", "prop-b", "prop-c")) {
			script.add(toolUseWrite());
			script.add(finalAnswerResponse(readyResultEnvelope(localRef)));
		}
		mockAiProvider.script(script);

		WebsiteGenerationOutcome outcome = drivingService.generate(projectId, repositoryRoot, workspacesRoot);

		assertThat(outcome.allSucceeded()).isTrue();
		assertThat(outcome.siblings()).hasSize(3);
		for (WebsiteGenerationSiblingOutcome sibling : outcome.siblings()) {
			assertThat(sibling.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
			assertThat(sibling.candidate()).isNotNull();
			assertThat(sibling.candidate().getSourceDesignProposalLocalRef()).isEqualTo(sibling.proposalLocalRef());
			assertThat(candidateRepository.findByAgentExecutionId(sibling.execution().getId())).contains(sibling.candidate());
		}
		assertThat(outcome.siblings()).extracting(WebsiteGenerationSiblingOutcome::proposalLocalRef)
				.containsExactly("prop-a", "prop-b", "prop-c");
	}

	@Test
	void oneSiblingsInfrastructureFailureNeverPreventsTheOtherTwoFromRunning(
			@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");

		// architech.developer.initial-generation.max-correction-cycles-per-sibling is 2 in
		// application.yml, so prop-b's own always-wrong output-contract envelope must be scripted
		// three times (the initial attempt plus both correction cycles) to actually exhaust its
		// budget and end FAILED with no candidate - matching
		// DeveloperToolLoopOrchestratorIT#anOutputContractViolationTriggersCorrectionThenSucceeds's
		// own proof that each correction cycle consumes exactly one more MockAiProvider response.
		// This test proves the milder, adjacent guarantee: prop-a/prop-c still succeed regardless.
		mockAiProvider.script(List.of(
				toolUseWrite(),
				finalAnswerResponse(readyResultEnvelope("prop-a")),
				finalAnswerResponse("{\"wrong-key\": {}}"),
				finalAnswerResponse("{\"wrong-key\": {}}"),
				finalAnswerResponse("{\"wrong-key\": {}}"),
				toolUseWrite(),
				finalAnswerResponse(readyResultEnvelope("prop-c"))));

		WebsiteGenerationOutcome outcome = drivingService.generate(projectId, repositoryRoot, workspacesRoot);

		assertThat(outcome.allSucceeded()).isFalse();
		WebsiteGenerationSiblingOutcome a = outcome.siblings().get(0);
		WebsiteGenerationSiblingOutcome b = outcome.siblings().get(1);
		WebsiteGenerationSiblingOutcome c = outcome.siblings().get(2);
		assertThat(a.candidate()).isNotNull();
		assertThat(b.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(b.candidate()).isNull();
		assertThat(c.candidate()).isNotNull();
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

	private UUID designProposalSetVersionId;

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
