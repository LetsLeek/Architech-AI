package ai.architech.backend.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.toolexecution.ToolCapability;
import ai.architech.backend.core.toolexecution.ToolExecution;
import ai.architech.backend.core.toolexecution.ToolExecutionRepository;
import ai.architech.backend.core.toolexecution.ToolExecutionStatus;
import ai.architech.backend.core.validation.DeveloperResultValidationIssue;
import ai.architech.backend.core.validation.DeveloperResultValidationPersister;
import ai.architech.backend.core.validation.DeveloperResultValidationResult;
import ai.architech.backend.core.verification.RunnerVerificationGateRecordRepository;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import ai.architech.backend.core.verification.VerificationOutcome;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves AIW-161's own "tests verify correlation across success, correction, workflow retry,
 * semantic BLOCKED and infrastructure ERROR paths" acceptance criterion against real Postgres
 * rows written through each evidence table's own repository/persister - not a mocked
 * approximation of the join.
 */
@SpringBootTest
@Transactional
class DeveloperExecutionAuditTrailAssemblerIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private ToolExecutionRepository toolExecutionRepository;

	@Autowired
	private DeveloperResultValidationPersister validationPersister;

	@Autowired
	private RunnerVerificationRunRepository runnerVerificationRunRepository;

	@Autowired
	private RunnerVerificationGateRecordRepository runnerVerificationGateRecordRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private DeveloperExecutionAuditTrailAssembler assembler;

	@Test
	void assemblesTheFullTraceForASuccessfulCandidateBearingExecution() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project.getId());
		ToolExecution toolExecution = toolExecutionRepository.saveAndFlush(
				new ToolExecution(
						execution.getId(),
						ToolCapability.FILESYSTEM,
						"write_file",
						0,
						ToolExecutionStatus.SUCCEEDED,
						"wrote src/pages/Home.tsx",
						Instant.now(),
						Instant.now()));
		validationPersister.persist(execution.getId(), DeveloperResultValidationResult.passed());
		RunnerVerificationRun verificationRun = runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(execution.getId(), "snapshot-hash-1", VerificationOutcome.PASS));
		execution.succeed();
		agentExecutionRepository.saveAndFlush(execution);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				project.getId(),
				execution.getId(),
				"design-v1",
				"prop-a",
				"runtime-v1",
				"snapshot-hash-1",
				"summary",
				"[]",
				"[]",
				"[]"));

		DeveloperExecutionAuditTrail trail = assembler.assemble(execution.getId());

		assertThat(trail.execution().getId()).isEqualTo(execution.getId());
		assertThat(trail.toolExecutions()).extracting(ToolExecution::getId).containsExactly(toolExecution.getId());
		assertThat(trail.validationRecords()).hasSize(1);
		assertThat(trail.validationRecords().getFirst().isValid()).isTrue();
		assertThat(trail.verificationRuns()).hasSize(1);
		assertThat(trail.verificationRuns().getFirst().run().getId()).isEqualTo(verificationRun.getId());
		assertThat(trail.verificationRuns().getFirst().run().getRepositoryStateRef()).isEqualTo("snapshot-hash-1");
		assertThat(trail.candidate()).isNotNull();
		assertThat(trail.candidate().getId()).isEqualTo(candidate.getId());
		assertThat(trail.candidate().getRepositoryStateRef()).isEqualTo(trail.verificationRuns().getFirst().run().getRepositoryStateRef());
	}

	@Test
	void accumulatesEvidenceAcrossACorrectionCycleWithoutOverwritingTheEarlierAttempt() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project.getId());
		validationPersister.persist(
				execution.getId(),
				new DeveloperResultValidationResult(
						false, List.of(new DeveloperResultValidationIssue("schema", "$.foo", "missing"))));
		RunnerVerificationRun firstAttempt = runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(execution.getId(), "snapshot-hash-1", VerificationOutcome.FAIL));
		execution.authorizeCorrectionCycle(3);
		validationPersister.persist(execution.getId(), DeveloperResultValidationResult.passed());
		RunnerVerificationRun secondAttempt = runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(execution.getId(), "snapshot-hash-2", VerificationOutcome.PASS));
		execution.succeed();
		agentExecutionRepository.saveAndFlush(execution);

		DeveloperExecutionAuditTrail trail = assembler.assemble(execution.getId());

		assertThat(trail.validationRecords()).hasSize(2);
		assertThat(trail.validationRecords().get(0).isValid()).isFalse();
		assertThat(trail.validationRecords().get(1).isValid()).isTrue();
		assertThat(trail.verificationRuns()).extracting(v -> v.run().getId())
				.containsExactly(firstAttempt.getId(), secondAttempt.getId());
		assertThat(trail.verificationRuns()).extracting(v -> v.run().getOutcome())
				.containsExactly(VerificationOutcome.FAIL, VerificationOutcome.PASS);
	}

	@Test
	void workflowRetryProducesAnIndependentTraceWithoutTouchingThePriorExecutionsEvidence() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution first = startedExecution(project.getId());
		toolExecutionRepository.saveAndFlush(new ToolExecution(
				first.getId(), ToolCapability.PROJECT_EXECUTION, "npm_run_build", 0, ToolExecutionStatus.FAILED,
				"tsc reported 3 errors", Instant.now(), Instant.now()));
		first.fail("output-contract validation failed");
		agentExecutionRepository.saveAndFlush(first);

		AgentExecution retry = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "developer-agent", 1, first.getId(), "RESULT_VALIDATION_FAILURE"));
		retry.start();
		toolExecutionRepository.saveAndFlush(new ToolExecution(
				retry.getId(), ToolCapability.PROJECT_EXECUTION, "npm_run_build", 0, ToolExecutionStatus.SUCCEEDED,
				"build succeeded", Instant.now(), Instant.now()));
		retry.succeed();
		agentExecutionRepository.saveAndFlush(retry);

		DeveloperExecutionAuditTrail firstTrail = assembler.assemble(first.getId());
		DeveloperExecutionAuditTrail retryTrail = assembler.assemble(retry.getId());

		assertThat(firstTrail.execution().getStatus().name()).isEqualTo("FAILED");
		assertThat(firstTrail.toolExecutions()).hasSize(1);
		assertThat(firstTrail.toolExecutions().getFirst().getStatus()).isEqualTo(ToolExecutionStatus.FAILED);
		assertThat(retryTrail.execution().getRetryOfExecutionId()).isEqualTo(first.getId());
		assertThat(retryTrail.toolExecutions()).hasSize(1);
		assertThat(retryTrail.toolExecutions().getFirst().getStatus()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		// Re-reading the original execution's own trail again proves the retry never mutated it.
		assertThat(assembler.assemble(first.getId()).toolExecutions()).hasSize(1);
		assertThat(assembler.assemble(first.getId()).execution().getStatus().name()).isEqualTo("FAILED");
	}

	@Test
	void aSemanticallyBlockedExecutionIsFullyTraceableWithNoCandidate() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project.getId());
		toolExecutionRepository.saveAndFlush(new ToolExecution(
				execution.getId(), ToolCapability.GIT_INSPECTION, "git_diff", 0, ToolExecutionStatus.SUCCEEDED,
				"confirmed no local workaround exists", Instant.now(), Instant.now()));
		validationPersister.persist(execution.getId(), DeveloperResultValidationResult.passed());
		execution.block("integration contract required for payment provider is missing");
		agentExecutionRepository.saveAndFlush(execution);

		DeveloperExecutionAuditTrail trail = assembler.assemble(execution.getId());

		assertThat(trail.execution().getStatus().name()).isEqualTo("BLOCKED");
		assertThat(trail.execution().getFailureReason())
				.isEqualTo("integration contract required for payment provider is missing");
		assertThat(trail.toolExecutions()).hasSize(1);
		assertThat(trail.validationRecords()).hasSize(1);
		assertThat(trail.verificationRuns()).isEmpty();
		assertThat(trail.candidate()).isNull();
	}

	@Test
	void anInfrastructureErrorIsTraceableEvenWithNoEvidenceProducedYet() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = new AgentExecution(project.getId(), "developer-agent", 1);
		execution.error("sandbox failed to start");
		agentExecutionRepository.saveAndFlush(execution);

		DeveloperExecutionAuditTrail trail = assembler.assemble(execution.getId());

		assertThat(trail.execution().getStatus().name()).isEqualTo("ERROR");
		assertThat(trail.execution().getFailureReason()).isEqualTo("sandbox failed to start");
		assertThat(trail.toolExecutions()).isEmpty();
		assertThat(trail.validationRecords()).isEmpty();
		assertThat(trail.verificationRuns()).isEmpty();
		assertThat(trail.candidate()).isNull();
	}

	@Test
	void rejectsAnUnknownExecutionId() {
		assertThatThrownBy(() -> assembler.assemble(UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
	}

	private AgentExecution startedExecution(UUID projectId) {
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}
}
