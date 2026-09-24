package ai.architech.backend.core.audit;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.toolexecution.ToolExecution;
import ai.architech.backend.core.validation.DeveloperResultValidationRecord;
import ai.architech.backend.core.verification.RunnerVerificationGateRecord;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import java.util.List;

/**
 * The full correlated evidence chain for one Website Developer {@link AgentExecution} (AIW-161):
 * Project (reachable via {@link AgentExecution#getProjectId()} - not duplicated here) →
 * {@code execution} → its {@code toolExecutions} → its {@code validationRecords} → its
 * {@code verificationRuns} (each already paired with its own gates) → {@code repositoryStateRef}
 * (carried on whichever verification run or Candidate evaluated it) → {@code candidate}.
 *
 * <p>{@code candidate} is {@code null} for every non-{@code SUCCEEDED} execution - a Candidate
 * cannot exist for one (AIW-159's own acceptance boundary) - but every other list is still
 * populated whatever the outcome, which is exactly what makes a {@code BLOCKED}/{@code FAILED}/
 * {@code ERROR} execution traceable with no Candidate at all (this ticket's own acceptance
 * criterion). An execution that failed before any tool call or validation ran (a pure
 * infrastructure {@code ERROR}) legitimately has empty lists here, not missing ones - {@link
 * DeveloperExecutionAuditTrailAssembler} never fabricates rows that were never produced.
 *
 * <p>Deliberately excludes raw AI request/response content: model/provider/token/cost telemetry
 * already lives on {@code execution} itself (never duplicated onto {@code candidate} - see
 * {@code WebsiteImplementationCandidate}'s own javadoc), and the AI Gateway's own correlation id
 * for every request this execution made is simply {@code execution.getId().toString()} - already
 * enough to join structured logs/traces to this trail without persisting a raw transcript here
 * (this ticket's own "avoid storing... full unbounded raw transcripts" acceptance criterion).
 *
 * <p>Every {@code repositoryStateRef} appearing in {@code verificationRuns}/{@code candidate} is
 * an opaque content-digest pointer into the workspace's git-tracked tree (see
 * {@code FrozenHandoffSnapshot}) - never a copy of repository content - which is what keeps
 * execution history (rows in this trail) and Git/repository state history (the tree the digest
 * points at) structurally separate rather than merely conventionally separate.
 */
public record DeveloperExecutionAuditTrail(
		AgentExecution execution,
		List<ToolExecution> toolExecutions,
		List<DeveloperResultValidationRecord> validationRecords,
		List<VerificationRunEvidence> verificationRuns,
		WebsiteImplementationCandidate candidate) {

	/** One {@link RunnerVerificationRun} together with its own ordered gate records. */
	public record VerificationRunEvidence(RunnerVerificationRun run, List<RunnerVerificationGateRecord> gates) {}
}
