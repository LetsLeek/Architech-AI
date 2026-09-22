package ai.architech.backend.core.runner;

import static org.assertj.core.api.Assertions.assertThat;

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
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.projecttype.website.DeveloperExecutionInputAssembler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A dedicated context (via {@code @TestPropertySource}) with a deliberately tiny {@code
 * max-turns} bound, so budget exhaustion is reachable without an unreasonably long scripted
 * sequence - kept in its own file rather than added to {@link DeveloperToolLoopOrchestratorIT}
 * specifically so that class's own tests keep using the platform's real, generous default bound.
 */
@SpringBootTest
@TestPropertySource(properties = "architech.developer.tool-loop.max-turns=1")
@Transactional
class DeveloperToolLoopOrchestratorBudgetExhaustionIT {

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
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void initRealGitRepo() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
	}

	@AfterEach
	void resetMockScripts() {
		mockAiProvider.resetScripts();
	}

	@Test
	void exhaustingTheTurnBudgetEndsTheExecutionFailedNeverLoopingForever() {
		UUID projectId = seedProject();
		String executionInput = assembler.assemble(projectId, "prop-a", "commit-sha-fixture", 3);

		ObjectNode listInput = objectMapper.createObjectNode();
		listInput.put("operation", "list").put("path", ".");
		// Only ONE scripted response - with max-turns=1, the loop must never even attempt a
		// second real call before recognizing the budget is exhausted.
		mockAiProvider.script(List.of(new AiResponse(
				"mock", "mock-model", "", null, null, null, null, null, "tool_use",
				List.of(new ContentBlock.ToolUse("call-1", "filesystem", listInput)))));

		DeveloperToolLoopResult result = orchestrator.run(projectId, executionInput, new Workspace(root));

		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.candidate()).isNull();
		assertThat(candidateRepository.findByAgentExecutionId(result.execution().getId())).isEmpty();
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

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
