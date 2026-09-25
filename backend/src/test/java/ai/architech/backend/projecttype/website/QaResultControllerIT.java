package ai.architech.backend.projecttype.website;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.security.DefaultApiKeyHeaderConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only API coverage for {@link QaResultController} (AIW-208) - mirrors {@code
 * DeveloperExecutionStatusControllerIT}'s own conventions (real MockMvc, real Postgres, no live
 * model call: the one persisted {@link QaResult} used here is built the same way {@link
 * QaResultAssemblyServiceIT} already proves for real, via {@link
 * QaSemanticReviewOrchestrator#validateAndRecord} plus {@link QaResultAssemblyService#assemble}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DefaultApiKeyHeaderConfig.class) // AIW-185: attaches a valid X-API-Key to every MockMvc request by default
@Transactional
class QaResultControllerIT {

	private static final String QA_EXECUTION_INPUT =
			"""
			{
			  "schemaVersion": "1.0.0",
			  "qaExecutionRef": "qa-exec-209",
			  "target": {"candidateRef": "candidate-209"},
			  "productAuthority": {
			    "customerProfileRef": "customer-profile-7",
			    "websiteRequirementsRef": "requirements-11",
			    "sourceDesignRef": "design-b-3",
			    "runtimeProfileRef": "runtime-react-vite-1",
			    "integrationContractRefs": []
			  },
			  "qaAuthority": {
			    "qaProfileRef": "website-qa-full-release@1.0.0",
			    "ruleSetRef": "website-qa-rules@1.0.0",
			    "skillSetRef": "website-qa-skills@1.0.0",
			    "validatorSetRef": "website-qa-validator-set@1.0.0",
			    "findingTaxonomyRef": "website-qa-finding-taxonomy@1.0.0",
			    "checkRegistryRef": "website-qa-check-registry@1.0.0"
			  },
			  "executionContext": {
			    "executionSurfaceRef": "preview-42",
			    "toolCapabilityProfileRef": "website-qa-tools@1.0.0"
			  },
			  "provenance": {
			    "inputSnapshotRef": "qa-input-snapshot-209",
			    "qaAgentVersion": "1.0.0"
			  }
			}
			""";

	private static final String CANDIDATE_BODY =
			"""
			{
			  "schemaVersion": "1.0.0",
			  "qaExecutionRef": "qa-exec-209",
			  "testedCandidateRef": "candidate-209",
			  "qaProfileRef": "website-qa-full-release@1.0.0",
			  "inputSnapshotRef": "qa-input-snapshot-209",
			  "findingCandidates": [],
			  "authorityIssueCandidates": [],
			  "evaluationIssueCandidates": [],
			  "semanticReviewCoverage": [
			    {"reviewTaskRef": "review-navigation-1", "domain": "NAVIGATION", "status": "COMPLETED", "evidenceRefs": ["ev-1"]}
			  ]
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaSemanticReviewOrchestrator orchestrator;

	@Autowired
	private QaResultAssemblyService assemblyService;

	@Test
	void rejectsAnUnknownProject() throws Exception {
		mockMvc.perform(get("/api/projects/{projectId}/qa-results", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void reportsNotFoundWhenTheProjectHasNoQaResultYet() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(get("/api/projects/{projectId}/qa-results", project.getId())).andExpect(status().isNotFound());
	}

	@Test
	void returnsTheProjectsLatestPersistedQaResult() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution developerExecution = new AgentExecution(project.getId(), "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				project.getId(),
				developerExecution.getId(),
				"design-v1",
				"prop-a",
				"runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(),
				"summary",
				"[]",
				"[]",
				"[]"));

		AgentExecution qaAgentExecution = new AgentExecution(project.getId(), "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(),
				candidate.getId(),
				"website-qa-full-release@1.0.0",
				"preview-42",
				"website-qa-tools@1.0.0"));
		qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), QA_EXECUTION_INPUT));

		String candidateOutput = "{\"semantic-qa-review-output\": " + CANDIDATE_BODY + "}";
		QaSemanticReviewResult result = orchestrator.validateAndRecord(
				qaExecution, QA_EXECUTION_INPUT, new RunnerResult(qaAgentExecution, candidateOutput));
		QaResult qaResult = assemblyService.assemble(qaExecution, result.candidateOutput().getContent());

		mockMvc.perform(get("/api/projects/{projectId}/qa-results", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.qaResultId").value(qaResult.getId().toString()))
				.andExpect(jsonPath("$.qaExecutionId").value(qaExecution.getId().toString()))
				.andExpect(jsonPath("$.testedCandidateId").value(candidate.getId().toString()))
				.andExpect(jsonPath("$.qaProfileRef").value("website-qa-full-release@1.0.0"))
				.andExpect(jsonPath("$.evaluationState").value("COMPLETE"))
				.andExpect(jsonPath("$.gateOutcome").value("PASS"))
				.andExpect(jsonPath("$.holdReasons").isEmpty())
				.andExpect(jsonPath("$.findingCount").value(0))
				.andExpect(jsonPath("$.authorityIssueCount").value(0))
				.andExpect(jsonPath("$.evaluationIssueCount").value(0));
	}
}
