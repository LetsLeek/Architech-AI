package ai.architech.backend.projecttype.website;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationLine;
import ai.architech.backend.core.documentation.canonical.DocumentationLineRepository;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersion;
import ai.architech.backend.core.documentation.canonical.DocumentationPackageVersionRepository;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.context.DocumentationContextRepository;
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
 * Read-only API coverage for {@link DocumentationController} (AIW-209) - mirrors {@code
 * QaResultControllerIT}'s own conventions (real MockMvc, real Postgres, no live model call). A
 * real {@link DocumentationPackageVersion} row has real foreign keys onto {@link
 * DocumentationLine} and {@link DocumentationContext}, and {@link DocumentationContext} itself has
 * real foreign keys onto {@code WebsiteImplementationCandidate} and {@link QaResult} (see {@code
 * V24__create_documentation_context.sql}/{@code V25__create_documentation_package_version.sql}) -
 * so this test builds that whole real chain (candidate -> QA result -> Documentation context ->
 * Documentation line/package version) the same way {@code QaResultControllerIT} already builds the
 * candidate -> QA result half of it, rather than pointing any of these at a non-existent id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DefaultApiKeyHeaderConfig.class) // AIW-185: attaches a valid X-API-Key to every MockMvc request by default
@Transactional
class DocumentationControllerIT {

	private static final String QA_EXECUTION_INPUT =
			"""
			{
			  "schemaVersion": "1.0.0",
			  "qaExecutionRef": "qa-exec-209-doc",
			  "target": {"candidateRef": "candidate-209-doc"},
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
			    "inputSnapshotRef": "qa-input-snapshot-209-doc",
			    "qaAgentVersion": "1.0.0"
			  }
			}
			""";

	private static final String CANDIDATE_BODY =
			"""
			{
			  "schemaVersion": "1.0.0",
			  "qaExecutionRef": "qa-exec-209-doc",
			  "testedCandidateRef": "candidate-209-doc",
			  "qaProfileRef": "website-qa-full-release@1.0.0",
			  "inputSnapshotRef": "qa-input-snapshot-209-doc",
			  "findingCandidates": [],
			  "authorityIssueCandidates": [],
			  "evaluationIssueCandidates": [],
			  "semanticReviewCoverage": [
			    {"reviewTaskRef": "review-navigation-1", "domain": "NAVIGATION", "status": "COMPLETED", "evidenceRefs": ["ev-1"]}
			  ]
			}
			""";

	private static final String DOCUMENTATION_CONTEXT_CONTENT = "{\"schemaVersion\": \"1.0.0\"}";

	private static final String PACKAGE_VERSION_CONTENT =
			"""
			{
			  "schemaVersion": "1.0.0",
			  "packageId": "pkg-209",
			  "documentationLineRef": "line-209",
			  "revision": 1,
			  "projectRef": "project-209",
			  "contextRef": "context-209",
			  "profileRef": "TECHNICAL_HANDOVER@1.0.0",
			  "policyRef": "documentation-policy@1.0.0",
			  "audience": "DEVELOPER",
			  "locale": "en-GB",
			  "semanticArtifactRefs": ["semantic-1"],
			  "deterministicReportRefs": ["report-1", "report-2"],
			  "validationResultRef": "validation-1",
			  "originRunRef": "run-1",
			  "generationReasons": ["INITIAL"],
			  "createdAt": "2026-09-25T00:00:00Z"
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

	@Autowired
	private DocumentationContextRepository documentationContextRepository;

	@Autowired
	private DocumentationLineRepository documentationLineRepository;

	@Autowired
	private DocumentationPackageVersionRepository documentationPackageVersionRepository;

	@Test
	void rejectsAnUnknownProject() throws Exception {
		mockMvc.perform(get("/api/projects/{projectId}/documentation-packages", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void returnsAnEmptyListWhenTheProjectHasNoDocumentationYet() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(get("/api/projects/{projectId}/documentation-packages", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void returnsEachDocumentationLineWithItsCurrentPackageVersionSummary() throws Exception {
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
				qaAgentExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", "preview-42", "website-qa-tools@1.0.0"));
		qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), QA_EXECUTION_INPUT));

		String candidateOutput = "{\"semantic-qa-review-output\": " + CANDIDATE_BODY + "}";
		QaSemanticReviewResult result = orchestrator.validateAndRecord(
				qaExecution, QA_EXECUTION_INPUT, new RunnerResult(qaAgentExecution, candidateOutput));
		QaResult qaResult = assemblyService.assemble(qaExecution, result.candidateOutput().getContent());

		DocumentationContext context = documentationContextRepository.saveAndFlush(new DocumentationContext(
				UUID.randomUUID(),
				project.getId(),
				candidate.getId(),
				qaResult.getId(),
				"TECHNICAL_HANDOVER@1.0.0",
				1,
				DOCUMENTATION_CONTEXT_CONTENT));

		DocumentationLine line = documentationLineRepository.saveAndFlush(
				new DocumentationLine(project.getId(), "TECHNICAL_HANDOVER@1.0.0", "en-GB"));

		DocumentationPackageVersion packageVersion = documentationPackageVersionRepository.saveAndFlush(new DocumentationPackageVersion(
				UUID.randomUUID(),
				line.getId(),
				1,
				project.getId(),
				context.getId(),
				"TECHNICAL_HANDOVER@1.0.0",
				"idempotency-key-209",
				null,
				PACKAGE_VERSION_CONTENT));

		line.advance(packageVersion.getId(), 1);
		documentationLineRepository.saveAndFlush(line);

		mockMvc.perform(get("/api/projects/{projectId}/documentation-packages", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].documentationLineId").value(line.getId().toString()))
				.andExpect(jsonPath("$[0].profileRef").value("TECHNICAL_HANDOVER@1.0.0"))
				.andExpect(jsonPath("$[0].locale").value("en-GB"))
				.andExpect(jsonPath("$[0].currentRevision").value(1))
				.andExpect(jsonPath("$[0].currentPackageVersion.packageVersionId").value(packageVersion.getId().toString()))
				.andExpect(jsonPath("$[0].currentPackageVersion.revision").value(1))
				.andExpect(jsonPath("$[0].currentPackageVersion.audience").value("DEVELOPER"))
				.andExpect(jsonPath("$[0].currentPackageVersion.policyRef").value("documentation-policy@1.0.0"))
				.andExpect(jsonPath("$[0].currentPackageVersion.semanticArtifactCount").value(1))
				.andExpect(jsonPath("$[0].currentPackageVersion.deterministicReportCount").value(2))
				.andExpect(jsonPath("$[0].currentPackageVersion.generationReasons[0]").value("INITIAL"));
	}
}
