package ai.architech.backend.core.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.ai.ContentBlock;
import ai.architech.backend.core.ai.MockAiProvider;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.core.toolexecution.ToolExecution;
import ai.architech.backend.core.toolexecution.ToolExecutionRepository;
import ai.architech.backend.core.toolexecution.ToolExecutionStatus;
import ai.architech.backend.core.verification.AuthoritativeRunnerVerifier;
import ai.architech.backend.core.verification.GateResult;
import ai.architech.backend.core.verification.RunnerVerificationResult;
import ai.architech.backend.core.verification.VerificationOutcome;
import ai.architech.backend.projecttype.website.DeveloperExecutionInputAssembler;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Fixture-driven, zero-live-Anthropic-call proof of AIW-184's own multi-turn tool-calling loop -
 * every scenario scripted through {@link MockAiProvider}, exactly the posture {@code
 * InitialGenerationOrchestratorIT}/{@code WebsiteQaEndToEndVerticalSliceIT} already established
 * for pipeline stages that don't yet have a real live model run. {@link AuthoritativeRunnerVerifier}
 * is stubbed (never the real, expensive real-npm/real-browser call {@code
 * AuthoritativeRunnerVerifierIT} already exhaustively covers) so this class stays focused on the
 * loop's own mechanics: turn/tool-call dispatch, capability-profile enforcement, and the handoff
 * into the already-proven validation/verification-evidence/acceptance pipeline.
 */
@SpringBootTest
@Transactional
class DeveloperToolLoopOrchestratorIT {

	private static final String CUSTOMER_PROFILE =
			"""
			{
			  "business": {"name": "Green Leaf Cafe"},
			  "contact": {"phone": "+43 1 2345678"},
			  "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}],
			  "offerings": [{"localRef": "cust-off-1", "name": "Coffee"}],
			  "openingHours": [], "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": []
			}
			""";

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{
			  "goals": [], "targetAudiences": [], "contentRequirements": [],
			  "functionalRequirements": [
			    {"localRef": "req-func-1", "type": "booking", "description": "Let customers book a table", "strength": "must", "sourceRefs": ["s1"]}
			  ],
			  "languages": [], "constraints": [], "unknowns": [], "conflicts": []
			}
			""";

	private static final String PROPOSAL_SET_JSON =
			"""
			{"proposals": [
			  {
			    "localRef": "prop-a",
			    "name": "Warm Minimal",
			    "concept": "A calm, minimal layout emphasizing the menu.",
			    "websitePlan": {
			      "requirementRefs": ["req-func-1"],
			      "pages": [
			        {
			          "localRef": "page-a-home", "name": "Home", "route": "/",
			          "purpose": "Introduce the cafe and lead to booking",
			          "sections": [
			            {
			              "localRef": "sec-a-hero", "kind": "hero", "purpose": "Welcome visitors",
			              "layoutIntent": "centered, single column",
			              "elements": [{"localRef": "el-a-heading", "kind": "heading", "role": "title", "contentIntent": "Welcome"}]
			            }
			          ]
			        }
			      ]
			    },
			    "designSpecification": {
			      "colors": [{"role": "primary", "value": "#2f4f2f"}],
			      "typography": [{"role": "heading", "fontFamily": "Fraunces", "fontWeight": 600, "fontSizeRem": 2.2, "lineHeight": 1.2}],
			      "spacing": [{"role": "section", "valueRem": 3}],
			      "layout": {"contentWidth": "narrow", "density": "spacious", "pageGutterRem": 1.5, "sectionGapRem": 3, "gridIntent": "single column"},
			      "uiPatterns": [],
			      "imagery": {"direction": "warm, natural tones", "treatment": "soft-edged photography"},
			      "responsive": {
			        "navigationBehavior": "collapse into a menu icon", "contentStacking": "vertical", "typeScaling": "fluid clamp()",
			        "spacingAdjustment": "reduce by a third", "mediaBehavior": "scale to container"
			      }
			    }
			  }
			]}
			""";

	@TempDir
	Path root;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private DeveloperExecutionInputAssembler assembler;

	@Autowired
	private DeveloperToolLoopOrchestrator orchestrator;

	@Autowired
	private MockAiProvider mockAiProvider;

	@Autowired
	private ToolExecutionRepository toolExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthoritativeRunnerVerifier authoritativeRunnerVerifier;

	@BeforeEach
	void initRealGitRepo() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
		Files.writeString(root.resolve("package.json"), "{\"name\":\"tmp\",\"scripts\":{\"build\":\"echo build-ok\"}}");
		run(root, "git", "add", "package.json");
	}

	@AfterEach
	void resetMockScripts() {
		mockAiProvider.resetScripts();
	}

	@Test
	void aCleanMultiTurnToolUseSequenceReachesAnAcceptedCandidate() {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		String designArtifactVersionRef = targetDesignRef(executionInput);

		ObjectNode writeInput = objectMapper.createObjectNode();
		writeInput.put("operation", "write").put("path", "src/pages/Home.tsx").put("content", "export const Home = () => null;");
		ObjectNode buildInput = objectMapper.createObjectNode();
		buildInput.put("task", "build");

		mockAiProvider.script(List.of(
				toolUseResponse(List.of(
						new ContentBlock.ToolUse("call-1", "filesystem", writeInput),
						new ContentBlock.ToolUse("call-2", "project_execution", buildInput))),
				finalAnswerResponse(readyResultEnvelope(designArtifactVersionRef, anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]"))));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(result.candidate()).isNotNull();
		assertThat(candidateRepository.findByAgentExecutionId(result.execution().getId())).contains(result.candidate());

		List<ToolExecution> toolExecutions = toolExecutionRepository.findByAgentExecutionIdOrderByStartedAtAsc(result.execution().getId());
		assertThat(toolExecutions).hasSize(2);
		assertThat(toolExecutions).allSatisfy(execution -> assertThat(execution.getStatus()).isEqualTo(ToolExecutionStatus.SUCCEEDED));
		assertThat(toolExecutions).extracting(ToolExecution::getToolName).containsExactly("filesystem", "project_execution");
	}

	@Test
	void aDeniedToolCallIsRefusedRecordedAndNeverSilentlyAllowedButTheLoopStillCompletes() {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		String designArtifactVersionRef = targetDesignRef(executionInput);
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");

		ObjectNode pushInput = objectMapper.createObjectNode();
		pushInput.put("command", "push");

		mockAiProvider.script(List.of(
				toolUseResponse(List.of(new ContentBlock.ToolUse("call-1", "git_inspect", pushInput))),
				finalAnswerResponse(readyResultEnvelope(designArtifactVersionRef, anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]"))));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		List<ToolExecution> toolExecutions = toolExecutionRepository.findByAgentExecutionIdOrderByStartedAtAsc(result.execution().getId());
		assertThat(toolExecutions).hasSize(1);
		assertThat(toolExecutions.get(0).getToolName()).isEqualTo("git_inspect");
		assertThat(toolExecutions.get(0).getStatus()).isEqualTo(ToolExecutionStatus.DENIED);
		// The denial never silently aborted or silently proceeded as if it had succeeded - the
		// loop still reached its own genuine, independently-verified terminal state.
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(result.candidate()).isNotNull();
	}

	@Test
	void aRejectedResultTriggersABoundedSelfCorrectionCycleThenSucceeds() {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 1);
		String designArtifactVersionRef = targetDesignRef(executionInput);
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");

		// Only a SECTION anchor - the mandatory PAGE anchor for "page-a-home" is missing, exactly
		// DeveloperResultValidatorIT#rejectsMissingPageCoverageAnchors's own scenario.
		String missingPageAnchor =
				"""
				[{"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}]""";

		mockAiProvider.script(List.of(
				finalAnswerResponse(readyResultEnvelope(designArtifactVersionRef, missingPageAnchor, bindingsJson("IMPLEMENTED_LOCAL"), "[]")),
				finalAnswerResponse(readyResultEnvelope(designArtifactVersionRef, anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]"))));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(result.execution().getCorrectionCyclesUsed()).isEqualTo(1);
		assertThat(result.candidate()).isNotNull();
	}

	@Test
	void aValidBlockedResultEndsTheExecutionBlockedWithNoCandidate() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		String designArtifactVersionRef = targetDesignRef(executionInput);

		String blocked =
				"""
				{"developer-agent-result": {
				  "resultType": "BLOCKED",
				  "targetDesign": {"designArtifactVersionRef": "%s", "proposalLocalRef": "prop-a"},
				  "blockers": [{"code": "UPSTREAM_CONFLICT", "relatedRequirementRefs": ["req-func-1"], "relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "diagnosticSummary": "Two canonical sources disagree on deposit policy"}]
				}}
				""".formatted(designArtifactVersionRef);
		mockAiProvider.script(List.of(finalAnswerResponse(blocked)));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.BLOCKED);
		assertThat(result.execution().getFailureReason()).contains("Two canonical sources disagree on deposit policy");
		assertThat(result.candidate()).isNull();
		assertThat(candidateRepository.findByAgentExecutionId(result.execution().getId())).isEmpty();
	}

	@Test
	void anOutputContractViolationTriggersCorrectionThenSucceeds() {
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(allGatesPass());
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 1);
		String designArtifactVersionRef = targetDesignRef(executionInput);
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");

		mockAiProvider.script(List.of(
				// Wrong top-level envelope key - fails output-contract validation before ever
				// reaching DeveloperResultValidator at all.
				finalAnswerResponse("{\"wrong-key\": {}}"),
				finalAnswerResponse(readyResultEnvelope(designArtifactVersionRef, anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]"))));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(result.execution().getCorrectionCyclesUsed()).isEqualTo(1);
		assertThat(result.candidate()).isNotNull();
	}

	@Test
	void aValidationFailureWithNoCorrectionBudgetEndsFailedImmediately() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 0);
		String designArtifactVersionRef = targetDesignRef(executionInput);
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");

		String missingPageAnchor =
				"""
				[{"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}]""";
		mockAiProvider.script(
				List.of(finalAnswerResponse(readyResultEnvelope(designArtifactVersionRef, missingPageAnchor, bindingsJson("IMPLEMENTED_LOCAL"), "[]"))));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.candidate()).isNull();
	}

	@Test
	void anOutputContractViolationWithNoCorrectionBudgetEndsFailedImmediately() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 0);
		mockAiProvider.script(List.of(finalAnswerResponse("{\"wrong-key\": {}}")));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.candidate()).isNull();
	}

	@Test
	void aFailingVerificationTriggersABoundedSelfCorrectionCycleThenSucceeds() {
		RunnerVerificationResult failing = new RunnerVerificationResult(
				List.of(GateResult.fail("typecheck (gate 3)", "exited 1: TS2322")));
		when(authoritativeRunnerVerifier.verify(any(), any(), any())).thenReturn(failing).thenReturn(allGatesPass());
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 1);
		String designArtifactVersionRef = targetDesignRef(executionInput);
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");

		String readyResult = readyResultEnvelope(designArtifactVersionRef, anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]");
		mockAiProvider.script(List.of(finalAnswerResponse(readyResult), finalAnswerResponse(readyResult)));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(result.execution().getCorrectionCyclesUsed()).isEqualTo(1);
		assertThat(result.candidate()).isNotNull();
	}

	@Test
	void aHardAiUsageBudgetBreachRefusesToEvenAttemptTheExecution() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		AgentExecution priorExecution = new AgentExecution(projectId, "developer-agent", 1);
		priorExecution.start();
		priorExecution.recordModelUsage("anthropic", "claude-sonnet-5", 1000, 1000, new BigDecimal("10.00"));
		priorExecution.succeed();
		agentExecutionRepository.saveAndFlush(priorExecution);

		assertThatThrownBy(() -> orchestrator.run(projectId, executionInput, new Workspace(root)))
				.isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AI_BUDGET_HARD_LIMIT_EXCEEDED));

		List<AgentExecution> executions = agentExecutionRepository.findAll().stream()
				.filter(execution -> execution.getProjectId().equals(projectId))
				.filter(execution -> "developer-agent".equals(execution.getAgentId()))
				.filter(execution -> !execution.getId().equals(priorExecution.getId()))
				.toList();
		assertThat(executions).singleElement().satisfies(execution -> assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.FAILED));
	}

	private RunnerVerificationResult allGatesPass() {
		return new RunnerVerificationResult(AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream().map(GateResult::pass).toList());
	}

	private AiResponse toolUseResponse(List<ContentBlock.ToolUse> toolUses) {
		return new AiResponse("mock", "mock-model", "", null, null, null, null, null, "tool_use", List.copyOf(toolUses));
	}

	private AiResponse finalAnswerResponse(String content) {
		return new AiResponse("mock", "mock-model", content, null, null, null, null, null);
	}

	private String readyResultEnvelope(String designArtifactVersionRef, String anchors, String bindings, String unresolvedIssues) {
		return """
				{"developer-agent-result": {
				  "resultType": "IMPLEMENTATION_READY",
				  "targetDesign": {"designArtifactVersionRef": "%s", "proposalLocalRef": "prop-a"},
				  "implementationSummary": "Implemented the home page hero.",
				  "implementationAnchors": %s,
				  "functionalBindings": %s,
				  "unresolvedIssues": %s
				}}
				""".formatted(designArtifactVersionRef, anchors, bindings, unresolvedIssues);
	}

	private String anchorsJson() {
		return """
				[
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx", "symbol": "HeroSection"}]}
				]""";
	}

	private String bindingsJson(String status) {
		return """
				[{"requirementRef": "req-func-1", "designLocalRefs": ["sec-a-hero"], "status": "%s"}]""".formatted(status);
	}

	private String targetDesignRef(String executionInput) {
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		return target.path("designArtifactVersionRef").asString();
	}

	private String assemble(UUID projectId, int maxCorrectionCycles) {
		return assembler.assemble(projectId, "prop-a", "commit-sha-fixture", maxCorrectionCycles);
	}

	private UUID seedProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);
		return project.getId();
	}

	private void persistArtifactVersion(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), content));
	}

	private void writeAndTrack(String relativePath, String content) {
		try {
			Path file = root.resolve(relativePath);
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
			run(root, "git", "add", relativePath);
		} catch (IOException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
