package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.AuthorityIssue;
import ai.architech.backend.core.qa.AuthorityIssueRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.EvaluationIssue;
import ai.architech.backend.core.qa.EvaluationIssueRepository;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.PolicyEvaluationRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.SchemaValidationResult;
import ai.architech.backend.core.validation.WebsiteQaSchemaRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real, end-to-end coverage for AIW-208's own conversion/persistence layer - proves {@link
 * QaResultAssemblyService} builds and persists a real {@link QaResult} plus real {@code
 * CandidateFinding}/{@code PolicyEvaluation} children from a genuine, schema-valid {@code
 * semantic-qa-review-output} candidate, never a synthetic fixture hand-built directly from
 * test-only {@code FindingSpec}-style records the way {@code WebsiteQaEndToEndVerticalSliceIT}
 * does, and never a hand-built {@code QaProfile} fixture (the real, frozen profile is loaded
 * through {@code QaProfileLoader}).
 *
 * <p>No live model call anywhere: {@link QaSemanticReviewOrchestrator#validateAndRecord} is
 * exercised directly with a hand-built {@link RunnerResult}, exactly the same "only the AI
 * Gateway call itself is bypassed" seam {@code QaSemanticReviewOrchestratorIT} already
 * establishes for this same orchestrator - the platform's mock AI provider is never invoked.
 */
@SpringBootTest
@Transactional
class QaResultAssemblyServiceIT {

	private static final String QA_EXECUTION_INPUT = """
			{
			  "schemaVersion": "1.0.0",
			  "qaExecutionRef": "qa-exec-208",
			  "target": {"candidateRef": "candidate-208"},
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
			    "inputSnapshotRef": "qa-input-snapshot-208",
			    "qaAgentVersion": "1.0.0"
			  }
			}
			""";

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
	private QaResultRepository qaResultRepository;

	@Autowired
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private AuthorityIssueRepository authorityIssueRepository;

	@Autowired
	private EvaluationIssueRepository evaluationIssueRepository;

	@Autowired
	private PolicyEvaluationRepository policyEvaluationRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private WebsiteQaSchemaRegistry schemaRegistry;

	private static final String NO_FINDINGS_COMPLETE_COVERAGE_BODY =
			"""
			{
			  "schemaVersion": "1.0.0",
			  "qaExecutionRef": "qa-exec-208",
			  "testedCandidateRef": "candidate-208",
			  "qaProfileRef": "website-qa-full-release@1.0.0",
			  "inputSnapshotRef": "qa-input-snapshot-208",
			  "findingCandidates": [],
			  "authorityIssueCandidates": [],
			  "evaluationIssueCandidates": [],
			  "semanticReviewCoverage": [
			    {"reviewTaskRef": "review-navigation-1", "domain": "NAVIGATION", "status": "COMPLETED", "evidenceRefs": ["ev-1"]}
			  ]
			}
			""";

	@Test
	void assemblesAPassingQaResultWithNoFindings() {
		QaExecution qaExecution = seedQaExecution();
		String body = NO_FINDINGS_COMPLETE_COVERAGE_BODY;
		assertSchemaValid(body);
		QaSemanticReviewResult result = runValidation(qaExecution, body);
		assertThat(result.succeeded()).as(result.issues().toString()).isTrue();

		QaResult qaResult = assemblyService.assemble(qaExecution, result.candidateOutput().getContent());

		assertThat(qaResult.getGateOutcome()).isEqualTo("PASS");
		assertThat(qaResult.getEvaluationState()).isEqualTo("COMPLETE");
		assertThat(qaResult.getHoldReasonsJson()).isEqualTo("[]");
		assertThat(qaResultRepository.findById(qaResult.getId())).isPresent();
		assertThat(candidateFindingRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId())).isEmpty();
		assertThat(authorityIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId())).isEmpty();
		assertThat(evaluationIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId())).isEmpty();
		assertThat(evidenceManifestRepository.findByQaExecutionId(qaExecution.getId())).isPresent();
	}

	@Test
	void aCriticalFindingProducesAHoldGateWithARealComputedFingerprint() {
		QaExecution qaExecution = seedQaExecution();
		String body =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "qaExecutionRef": "qa-exec-208",
				  "testedCandidateRef": "candidate-208",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "qa-input-snapshot-208",
				  "findingCandidates": [
				    {
				      "localRef": "finding-1",
				      "findingCode": "BROKEN_PRIMARY_NAV_LINK",
				      "primaryDomain": "NAVIGATION",
				      "proposedSeverity": "CRITICAL",
				      "normativeBasis": [{"type": "SOURCE_DESIGN", "ref": "design-b-3"}],
				      "summary": "The primary navigation link to the pricing page is broken.",
				      "evidenceRefs": ["ev-1"],
				      "context": {"route": "/pricing", "viewportRef": "FULL_WIDE"}
				    }
				  ],
				  "authorityIssueCandidates": [],
				  "evaluationIssueCandidates": [],
				  "semanticReviewCoverage": [
				    {"reviewTaskRef": "review-navigation-1", "domain": "NAVIGATION", "status": "COMPLETED", "evidenceRefs": ["ev-1"]}
				  ]
				}
				""";
		assertSchemaValid(body);
		QaSemanticReviewResult result = runValidation(qaExecution, body);
		assertThat(result.succeeded()).as(result.issues().toString()).isTrue();

		QaResult qaResult = assemblyService.assemble(qaExecution, result.candidateOutput().getContent());

		assertThat(qaResult.getGateOutcome()).isEqualTo("HOLD");
		assertThat(qaResult.getHoldReasonsJson()).contains("BLOCKING_CANDIDATE_FINDING");

		List<ai.architech.backend.core.qa.CandidateFinding> findings =
				candidateFindingRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId());
		assertThat(findings).hasSize(1);
		assertThat(findings.get(0).getFindingCode()).isEqualTo("BROKEN_PRIMARY_NAV_LINK");
		assertThat(findings.get(0).getSeverity()).isEqualTo("CRITICAL");
		assertThat(findings.get(0).getFingerprint()).isNotBlank().doesNotStartWith("fingerprint-");

		assertThat(policyEvaluationRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId())).hasSize(1);
	}

	@Test
	void parsesAuthorityAndEvaluationIssuesAndHandlesPartialCoverage() {
		QaExecution qaExecution = seedQaExecution();
		String body =
				"""
				{
				  "schemaVersion": "1.0.0",
				  "qaExecutionRef": "qa-exec-208",
				  "testedCandidateRef": "candidate-208",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "qa-input-snapshot-208",
				  "findingCandidates": [
				    {
				      "localRef": "finding-2",
				      "findingCode": "MINOR_COPY_TYPO",
				      "primaryDomain": "CONTENT_QUALITY",
				      "proposedSeverity": "MINOR",
				      "normativeBasis": [{"type": "WEBSITE_REQUIREMENT", "ref": "req-9"}],
				      "summary": "Minor copy typo on the homepage.",
				      "diagnosticDetails": "Found 'recieve' instead of 'receive'.",
				      "evidenceRefs": ["ev-2"]
				    }
				  ],
				  "authorityIssueCandidates": [
				    {
				      "localRef": "authority-1",
				      "code": "MISSING_AUTHORITY",
				      "summary": "No integration contract exists for the payment provider referenced by the design.",
				      "affectedAuthorityRefs": ["integration-contract-payment"],
				      "expectedAuthorityTypes": ["INTEGRATION_CONTRACT"],
				      "affectedDomains": ["INTEGRATION_BEHAVIOR"],
				      "evidenceRefs": ["ev-3"]
				    }
				  ],
				  "evaluationIssueCandidates": [
				    {
				      "localRef": "evaluation-1",
				      "code": "TOOL_FAILURE",
				      "summary": "The accessibility scanner tool crashed mid-run.",
				      "affectedDomains": ["ACCESSIBILITY_BASELINE"],
				      "requiredCheckRef": "A11Y_AUTOMATED_BASELINE",
				      "toolCapabilityRef": "website-qa-tools@1.0.0",
				      "executionSurfaceRef": "preview-42",
				      "evidenceRefs": ["ev-4"]
				    }
				  ],
				  "semanticReviewCoverage": [
				    {"reviewTaskRef": "review-content-1", "domain": "CONTENT_QUALITY", "status": "COMPLETED", "evidenceRefs": ["ev-2"]},
				    {"reviewTaskRef": "review-integration-1", "domain": "INTEGRATION_BEHAVIOR", "status": "PARTIAL", "evidenceRefs": ["ev-3"]},
				    {"reviewTaskRef": "review-a11y-1", "domain": "ACCESSIBILITY_BASELINE", "status": "NOT_EVALUABLE", "evidenceRefs": []}
				  ]
				}
				""";
		assertSchemaValid(body);
		QaSemanticReviewResult result = runValidation(qaExecution, body);
		assertThat(result.succeeded()).as(result.issues().toString()).isTrue();

		QaResult qaResult = assemblyService.assemble(qaExecution, result.candidateOutput().getContent());

		assertThat(qaResult.getEvaluationState()).isEqualTo("PARTIAL");
		assertThat(qaResult.getGateOutcome()).isEqualTo("HOLD");
		assertThat(qaResult.getHoldReasonsJson())
				.contains("EVALUATION_INCOMPLETE")
				.contains("AUTHORITY_RESOLUTION_REQUIRED")
				.contains("HUMAN_REVIEW_REQUIRED");
		assertThat(qaResult.getDomainResultsJson()).contains("CONTENT_QUALITY", "INTEGRATION_BEHAVIOR", "ACCESSIBILITY_BASELINE");

		List<CandidateFinding> findings = candidateFindingRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId());
		assertThat(findings).hasSize(1);
		assertThat(findings.get(0).getSeverity()).isEqualTo("MINOR");
		assertThat(findings.get(0).getDiagnosticDetails()).isEqualTo("Found 'recieve' instead of 'receive'.");
		assertThat(findings.get(0).getContextJson()).isNull();

		List<AuthorityIssue> authorityIssues = authorityIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId());
		assertThat(authorityIssues).hasSize(1);
		assertThat(authorityIssues.get(0).getCode()).isEqualTo("MISSING_AUTHORITY");

		List<EvaluationIssue> evaluationIssues = evaluationIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId());
		assertThat(evaluationIssues).hasSize(1);
		assertThat(evaluationIssues.get(0).getCode()).isEqualTo("TOOL_FAILURE");
		assertThat(evaluationIssues.get(0).getRequiredCheckRef()).isEqualTo("A11Y_AUTOMATED_BASELINE");
		assertThat(evaluationIssues.get(0).getToolCapabilityRef()).isEqualTo("website-qa-tools@1.0.0");
		assertThat(evaluationIssues.get(0).getExecutionSurfaceRef()).isEqualTo("preview-42");

		assertThat(policyEvaluationRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId())).hasSize(3);
	}

	@Test
	void reusesAnExistingEvidenceManifestOnASecondAssemblyForTheSameQaExecution() {
		QaExecution qaExecution = seedQaExecution();
		assertSchemaValid(NO_FINDINGS_COMPLETE_COVERAGE_BODY);
		QaSemanticReviewResult result = runValidation(qaExecution, NO_FINDINGS_COMPLETE_COVERAGE_BODY);
		assertThat(result.succeeded()).as(result.issues().toString()).isTrue();

		QaResult first = assemblyService.assemble(qaExecution, result.candidateOutput().getContent());
		QaResult second = assemblyService.assemble(qaExecution, result.candidateOutput().getContent());

		assertThat(second.getId()).isNotEqualTo(first.getId());
		assertThat(second.getEvidenceManifestId()).isEqualTo(first.getEvidenceManifestId());
		assertThat(evidenceManifestRepository.findAll().stream().filter(m -> m.getQaExecutionId().equals(qaExecution.getId())))
				.hasSize(1);
	}

	private void assertSchemaValid(String body) {
		SchemaValidationResult validation = schemaRegistry.validate("urn:aiw:schema:semantic-qa-review-output:v1", body);
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();
	}

	private QaSemanticReviewResult runValidation(QaExecution qaExecution, String body) {
		AgentExecution execution = agentExecutionRepository.findById(qaExecution.getAgentExecutionId()).orElseThrow();
		String candidateOutput = "{\"semantic-qa-review-output\": " + body + "}";
		return orchestrator.validateAndRecord(qaExecution, QA_EXECUTION_INPUT, new RunnerResult(execution, candidateOutput));
	}

	private QaExecution seedQaExecution() {
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
		return qaExecution;
	}
}
