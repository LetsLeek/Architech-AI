package ai.architech.backend.core.verification;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RunnerVerificationEvidencePersisterIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private RunnerVerificationEvidencePersister persister;

	@Autowired
	private RunnerVerificationRunRepository runRepository;

	@Autowired
	private RunnerVerificationGateRecordRepository gateRecordRepository;

	@Test
	void persistsAFullPassWithEveryGateRecordedAsPass() {
		UUID agentExecutionId = seedAgentExecution();
		RunnerVerificationResult result = new RunnerVerificationResult(
				AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream().map(GateResult::pass).toList());

		RunnerVerificationRun run = persister.persist(agentExecutionId, "snapshot-hash-pass", result);

		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.PASS);
		List<RunnerVerificationGateRecord> records =
				gateRecordRepository.findByRunnerVerificationRunIdOrderByGateOrderAsc(run.getId());
		assertThat(records).hasSize(14);
		assertThat(records).allSatisfy(record -> assertThat(record.getStatus()).isEqualTo(GateStatus.PASS));
		assertThat(records).extracting(RunnerVerificationGateRecord::getGateName)
				.isEqualTo(AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES);
	}

	@Test
	void persistsASourceFailureWithDownstreamGatesSkipped() {
		UUID agentExecutionId = seedAgentExecution();
		RunnerVerificationResult result = new RunnerVerificationResult(List.of(
				GateResult.pass("repository-dependency-integrity (gate 1)"),
				GateResult.pass("clean-policy-compliant-install (gate 2)"),
				GateResult.fail("typecheck (gate 3)", "exited 1: TS2322")));

		RunnerVerificationRun run = persister.persist(agentExecutionId, "snapshot-hash-fail", result);

		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.FAIL);
		List<RunnerVerificationGateRecord> records =
				gateRecordRepository.findByRunnerVerificationRunIdOrderByGateOrderAsc(run.getId());
		assertThat(records).hasSize(14);
		assertThat(records.get(0).getStatus()).isEqualTo(GateStatus.PASS);
		assertThat(records.get(1).getStatus()).isEqualTo(GateStatus.PASS);
		assertThat(records.get(2).getStatus()).isEqualTo(GateStatus.FAIL);
		assertThat(records.get(2).getDetail()).contains("TS2322");
		// Every gate after the one that failed never ran - persisted explicitly as SKIPPED,
		// not silently absent.
		assertThat(records.subList(3, 14)).allSatisfy(record -> assertThat(record.getStatus()).isEqualTo(GateStatus.SKIPPED));
	}

	@Test
	void persistsAnInfrastructureErrorWithDownstreamGatesSkipped() {
		UUID agentExecutionId = seedAgentExecution();
		RunnerVerificationResult result = new RunnerVerificationResult(List.of(
				GateResult.pass("repository-dependency-integrity (gate 1)"),
				GateResult.error("clean-policy-compliant-install (gate 2)", "sandbox failed to run the install task: timed out")));

		RunnerVerificationRun run = persister.persist(agentExecutionId, "snapshot-hash-error", result);

		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.ERROR);
		List<RunnerVerificationGateRecord> records =
				gateRecordRepository.findByRunnerVerificationRunIdOrderByGateOrderAsc(run.getId());
		assertThat(records.get(1).getStatus()).isEqualTo(GateStatus.ERROR);
		assertThat(records.subList(2, 14)).allSatisfy(record -> assertThat(record.getStatus()).isEqualTo(GateStatus.SKIPPED));
	}

	@Test
	void aSkippedMandatoryGateCanNeverBePersistedAsPartOfAPassingRun() {
		UUID agentExecutionId = seedAgentExecution();
		RunnerVerificationResult result = new RunnerVerificationResult(List.of(GateResult.pass("repository-dependency-integrity (gate 1)")));

		RunnerVerificationRun run = persister.persist(agentExecutionId, "snapshot-hash-partial", result);

		// The persister recomputes the run's outcome from the full, SKIPPED-filled gate list -
		// an incomplete RunnerVerificationResult (here, one that never even names a FAIL/ERROR
		// gate) is never laundered into a false PASS just because nothing present says otherwise.
		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.FAIL);
		List<RunnerVerificationGateRecord> records =
				gateRecordRepository.findByRunnerVerificationRunIdOrderByGateOrderAsc(run.getId());
		assertThat(records).filteredOn(record -> record.getStatus() == GateStatus.SKIPPED).hasSize(13);
	}

	@Test
	void supportsMultipleVerificationRunsForTheSameExecutionAcrossCorrectionCycles() {
		UUID agentExecutionId = seedAgentExecution();
		RunnerVerificationResult firstAttempt = new RunnerVerificationResult(
				List.of(GateResult.fail("typecheck (gate 3)", "TS2322")));
		RunnerVerificationResult secondAttempt = new RunnerVerificationResult(
				AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream().map(GateResult::pass).toList());

		persister.persist(agentExecutionId, "snapshot-hash-1", firstAttempt);
		persister.persist(agentExecutionId, "snapshot-hash-2", secondAttempt);

		assertThat(runRepository.findByAgentExecutionIdOrderByCreatedAtAsc(agentExecutionId))
				.extracting(RunnerVerificationRun::getRepositoryStateRef)
				.containsExactly("snapshot-hash-1", "snapshot-hash-2");
	}

	private UUID seedAgentExecution() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(project.getId(), "developer-agent", 1));
		return execution.getId();
	}
}
