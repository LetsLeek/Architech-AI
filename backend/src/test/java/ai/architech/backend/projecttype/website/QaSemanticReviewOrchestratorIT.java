package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.runner.RunnerResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration coverage for the Website QA semantic review pipeline (AIW-173). Only the AI
 * Gateway call itself is bypassed (a hand-built {@link RunnerResult}, same reasoning as {@code
 * DesignerAgentRunnerIT}); every validator/persistence step runs for real against Postgres. No
 * live LLM call - the platform's mock AI provider always returns empty content, which is exactly
 * what {@link #creatingRowsDoesNotDependOnTheModelCallSucceeding()} exercises through {@code
 * run()} itself.
 */
@SpringBootTest
@Transactional
class QaSemanticReviewOrchestratorIT {

	private static final String QA_EXECUTION_INPUT = """
			{
			  "schemaVersion": "1.0.0",
			  "qaExecutionRef": "qa-exec-101",
			  "target": {"candidateRef": "candidate-42"},
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
			    "inputSnapshotRef": "qa-input-snapshot-101",
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
	private CandidateOutputRepository candidateOutputRepository;

	@Autowired
	private QaSemanticReviewOrchestrator orchestrator;

	@Test
	void aWellFormedIdentityMatchingCandidateSucceeds() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		QaExecution qaExecution = seedQaExecution(project, execution);

		String candidateOutput = envelope(minimalValidBody());

		QaSemanticReviewResult result =
				orchestrator.validateAndRecord(qaExecution, QA_EXECUTION_INPUT, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).as(result.issues().toString()).isTrue();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(1);
	}

	@Test
	void failsAndPersistsNoCandidateWhenTheOutputIsNotValidJson() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		QaExecution qaExecution = seedQaExecution(project, execution);

		QaSemanticReviewResult result =
				orchestrator.validateAndRecord(qaExecution, QA_EXECUTION_INPUT, new RunnerResult(execution, "not json {{{"));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsButStillRecordsTheCandidateAsAuditWhenSchemaValidationFails() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		QaExecution qaExecution = seedQaExecution(project, execution);

		// Missing the required semanticReviewCoverage field - output-contract passes, schema fails.
		String body = """
				{
				  "schemaVersion": "1.0.0",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "qa-input-snapshot-101",
				  "findingCandidates": [],
				  "authorityIssueCandidates": [],
				  "evaluationIssueCandidates": []
				}
				""";
		String candidateOutput = envelope(body);

		QaSemanticReviewResult result =
				orchestrator.validateAndRecord(qaExecution, QA_EXECUTION_INPUT, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.issues()).anyMatch(issue -> issue.startsWith("schema:"));
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(1);
	}

	@Test
	void failsWhenTheCandidateClaimsADifferentQaProfileThanTheExecutionsOwnInput() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		QaExecution qaExecution = seedQaExecution(project, execution);

		String body = minimalValidBody().replace("website-qa-full-release@1.0.0", "website-qa-comparison-readiness@1.0.0");
		String candidateOutput = envelope(body);

		QaSemanticReviewResult result =
				orchestrator.validateAndRecord(qaExecution, QA_EXECUTION_INPUT, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.startsWith("identity: qaProfileRef"));
	}

	@Test
	void failsWhenTheCandidateClaimsADifferentTestedCandidateThanTheExecutionsOwnInput() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		QaExecution qaExecution = seedQaExecution(project, execution);

		String body = minimalValidBody().replace("\"candidate-42\"", "\"some-other-candidate\"");
		String candidateOutput = envelope(body);

		QaSemanticReviewResult result =
				orchestrator.validateAndRecord(qaExecution, QA_EXECUTION_INPUT, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.startsWith("identity: testedCandidateRef"));
	}

	@Test
	void creatingRowsDoesNotDependOnTheModelCallSucceeding() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		WebsiteImplementationCandidate candidate = seedCandidate(project);

		QaSemanticReviewResult result = orchestrator.run(
				project.getId(), candidate.getId(), "website-qa-full-release@1.0.0", "preview-42", "website-qa-tools@1.0.0", QA_EXECUTION_INPUT);

		// The platform's mock AI provider returns empty content, so this always fails - but the
		// QaExecution/QaInputSnapshot rows this method is responsible for creating must still exist.
		assertThat(result.succeeded()).isFalse();
		assertThat(result.qaExecution()).isNotNull();
		assertThat(qaExecutionRepository.findById(result.qaExecution().getId())).isPresent();
		assertThat(qaInputSnapshotRepository.findByQaExecutionIdOrderByCreatedAtAsc(result.qaExecution().getId())).hasSize(1);
	}

	private static String minimalValidBody() {
		return """
				{
				  "schemaVersion": "1.0.0",
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "qa-input-snapshot-101",
				  "findingCandidates": [],
				  "authorityIssueCandidates": [],
				  "evaluationIssueCandidates": [],
				  "semanticReviewCoverage": []
				}
				""";
	}

	private static String envelope(String body) {
		return "{\"semantic-qa-review-output\": " + body + "}";
	}

	private AgentExecution startedExecution(Project project) {
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(project.getId(), "website-qa-agent", 1));
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private QaExecution seedQaExecution(Project project, AgentExecution execution) {
		WebsiteImplementationCandidate candidate = seedCandidate(project);
		return qaExecutionRepository.saveAndFlush(new QaExecution(
				execution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", "preview-42", "website-qa-tools@1.0.0"));
	}

	private WebsiteImplementationCandidate seedCandidate(Project project) {
		AgentExecution developerExecution = new AgentExecution(project.getId(), "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
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
	}
}
