package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import java.util.UUID;

/**
 * What {@code DeveloperExecutionStatusController} returns for one Website Developer execution
 * (AIW-160). {@code status} is always {@link AgentExecution#getStatus()} - the platform's own
 * authoritative Runner lifecycle state - never a raw {@code resultType} the Developer's own
 * {@code developer-agent-result:v1} output might have self-declared (e.g. {@code
 * IMPLEMENTATION_READY}): those two are deliberately different things (this ticket's own "preserve
 * distinction between Agent result type and AgentExecution status" acceptance criterion), since a
 * self-declared {@code IMPLEMENTATION_READY} result only ever becomes {@link
 * AgentExecutionStatus#SUCCEEDED} once Runner Verification has also PASSed and a Candidate has
 * actually been durably persisted - see {@code WebsiteImplementationCandidateAcceptanceCoordinator}
 * (AIW-159).
 *
 * <p>{@code targetDesignArtifactVersionRef}/{@code targetDesignProposalLocalRef}/{@code
 * candidateId}/{@code repositoryStateRef} are only ever populated from an already-persisted {@link
 * WebsiteImplementationCandidate} - which exists only once an execution has reached {@code
 * SUCCEEDED} (AIW-145's own "exactly one Candidate per successful execution"). A still-{@code
 * RUNNING}/{@code BLOCKED}/{@code FAILED}/{@code ERROR} execution genuinely has no durable target-
 * design identity to report yet: there is no Developer-specific execution-context persistence in
 * this codebase today that records the target proposal ref before acceptance (the {@code
 * developer-execution-input.v1} payload AIW-151 assembles is built fresh in memory for one model
 * call and never stored) - those fields come back {@code null} rather than being fabricated from
 * whatever the in-flight request happened to be, honoring this ticket's own "null/not-yet-available
 * states are represented honestly" acceptance criterion. Closing that gap needs the still-unbuilt
 * Developer orchestration/persistence layer (see AIW-146/150/151's own open scope), not this
 * read-only status endpoint.
 *
 * <p>{@code failureReason} is always {@link AgentExecution#getFailureReason()} - a plain string
 * only ever written by Core validators/gates (a semantic blocker, a validation failure, or an
 * infrastructure error message), never raw model output or a tool payload - so it is safe to
 * return as-is; it is {@code null} for a still-{@code RUNNING} or {@code SUCCEEDED} execution.
 */
public record DeveloperExecutionStatusResponse(
		UUID executionId,
		String status,
		String targetDesignArtifactVersionRef,
		String targetDesignProposalLocalRef,
		UUID candidateId,
		String repositoryStateRef,
		String failureReason) {

	static DeveloperExecutionStatusResponse from(AgentExecution execution, WebsiteImplementationCandidate candidate) {
		if (execution.getStatus() == AgentExecutionStatus.SUCCEEDED && candidate == null) {
			throw new IllegalStateException(
					"AgentExecution " + execution.getId() + " is SUCCEEDED but has no accepted Candidate");
		}
		if (execution.getStatus() != AgentExecutionStatus.SUCCEEDED) {
			// A Candidate row can never exist for a non-SUCCEEDED execution (AIW-159's own
			// acceptance boundary), but even if it somehow did, this API never reports a
			// Candidate for BLOCKED/FAILED/ERROR/RUNNING executions (this ticket's own AC).
			candidate = null;
		}

		return new DeveloperExecutionStatusResponse(
				execution.getId(),
				execution.getStatus().name(),
				candidate == null ? null : candidate.getSourceDesignArtifactVersionRef(),
				candidate == null ? null : candidate.getSourceDesignProposalLocalRef(),
				candidate == null ? null : candidate.getId(),
				candidate == null ? null : candidate.getRepositoryStateRef(),
				execution.getFailureReason());
	}
}
