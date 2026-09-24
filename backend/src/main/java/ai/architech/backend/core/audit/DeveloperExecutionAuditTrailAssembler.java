package ai.architech.backend.core.audit;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.audit.DeveloperExecutionAuditTrail.VerificationRunEvidence;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.toolexecution.ToolExecutionRepository;
import ai.architech.backend.core.validation.DeveloperResultValidationRecordRepository;
import ai.architech.backend.core.verification.RunnerVerificationGateRecordRepository;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reassembles {@link DeveloperExecutionAuditTrail} for one {@code agentExecutionId} from every
 * already-persisted, independently-correlated evidence table this codebase has (AIW-161) - a
 * read-only join, never a new source of truth: every row it returns is exactly what {@code
 * ToolExecutionRepository}/{@code DeveloperResultValidationRecordRepository}/{@code
 * RunnerVerificationRunRepository}/{@code RunnerVerificationGateRecordRepository}/{@code
 * WebsiteImplementationCandidateRepository} already store, correlated the same way this whole
 * codebase already correlates Developer evidence: a plain {@code agentExecutionId} column, never
 * a JPA relationship.
 */
@Component
public class DeveloperExecutionAuditTrailAssembler {

	private final AgentExecutionRepository agentExecutionRepository;
	private final ToolExecutionRepository toolExecutionRepository;
	private final DeveloperResultValidationRecordRepository validationRecordRepository;
	private final RunnerVerificationRunRepository runnerVerificationRunRepository;
	private final RunnerVerificationGateRecordRepository runnerVerificationGateRecordRepository;
	private final WebsiteImplementationCandidateRepository candidateRepository;

	DeveloperExecutionAuditTrailAssembler(
			AgentExecutionRepository agentExecutionRepository,
			ToolExecutionRepository toolExecutionRepository,
			DeveloperResultValidationRecordRepository validationRecordRepository,
			RunnerVerificationRunRepository runnerVerificationRunRepository,
			RunnerVerificationGateRecordRepository runnerVerificationGateRecordRepository,
			WebsiteImplementationCandidateRepository candidateRepository) {
		this.agentExecutionRepository = agentExecutionRepository;
		this.toolExecutionRepository = toolExecutionRepository;
		this.validationRecordRepository = validationRecordRepository;
		this.runnerVerificationRunRepository = runnerVerificationRunRepository;
		this.runnerVerificationGateRecordRepository = runnerVerificationGateRecordRepository;
		this.candidateRepository = candidateRepository;
	}

	public DeveloperExecutionAuditTrail assemble(UUID agentExecutionId) {
		AgentExecution execution = agentExecutionRepository
				.findById(agentExecutionId)
				.orElseThrow(() -> new NoSuchElementException("No AgentExecution with id " + agentExecutionId));

		return new DeveloperExecutionAuditTrail(
				execution,
				toolExecutionRepository.findByAgentExecutionIdOrderByStartedAtAsc(agentExecutionId),
				validationRecordRepository.findByAgentExecutionIdOrderByCreatedAtAsc(agentExecutionId),
				runnerVerificationRunRepository.findByAgentExecutionIdOrderByCreatedAtAsc(agentExecutionId).stream()
						.map(this::withGates)
						.toList(),
				candidateRepository.findByAgentExecutionId(agentExecutionId).orElse(null));
	}

	private VerificationRunEvidence withGates(RunnerVerificationRun run) {
		return new VerificationRunEvidence(
				run, runnerVerificationGateRecordRepository.findByRunnerVerificationRunIdOrderByGateOrderAsc(run.getId()));
	}
}
