package ai.architech.backend.core.candidate;

import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.sandbox.Workspace;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The only path from a validated {@code IMPLEMENTATION_READY} {@code developer-agent-result:v1}
 * to an accepted {@link WebsiteImplementationCandidate} (AIW-145) - mirrors
 * {@code core.artifact.CandidatePromoter}'s own idiom exactly: this class does no result
 * validation itself. Callers are responsible for only ever calling {@link #promote} after
 * {@code DeveloperResultValidator} (AIW-142) has already accepted the result as
 * {@code IMPLEMENTATION_READY} <em>and</em> authoritative Runner Verification has PASSed - a
 * {@code BLOCKED}, {@code FAILED} or {@code ERROR} execution has no path to this method at all.
 *
 * <p>{@code repositoryStateRef} is exactly the {@link FrozenHandoffSnapshot#snapshotId()} Runner
 * Verification evaluated - not a separately computed value - which is what makes "verified
 * workspace state and persisted repositoryStateRef are deterministically equivalent" true by
 * construction. Before persisting, {@link HandoffFreezeGate#matchesCurrentState} is checked
 * again: if the workspace's tracked content no longer matches {@code snapshot} (something
 * mutated it between verification and this call), persistence is refused with {@link
 * RepositoryStateMismatchException} rather than silently persisting a state nobody actually
 * verified.
 *
 * <p>Idempotent/retry-safe by construction: an existing Candidate for {@code agentExecutionId}
 * is returned unchanged rather than duplicated, both via an upfront lookup and, for the race
 * where two callers reach this concurrently, by falling back to the same lookup if the unique
 * constraint on {@code agentExecutionId} rejects a concurrent insert.
 *
 * <p>A failure from {@link WebsiteImplementationCandidateRepository} that is <em>not</em> the
 * benign duplicate case propagates uncaught - classifying that as a Core/infrastructure error
 * rather than a Developer-owned failure (this ticket's own acceptance criteria) is the calling
 * Runner's responsibility once it exists, exactly the same boundary {@code AgentExecution.error}
 * vs. {@code .fail} already draws elsewhere in this codebase.
 */
@Component
public class WebsiteImplementationCandidatePromoter {

	private final WebsiteImplementationCandidateRepository repository;
	private final HandoffFreezeGate handoffFreezeGate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	WebsiteImplementationCandidatePromoter(
			WebsiteImplementationCandidateRepository repository, HandoffFreezeGate handoffFreezeGate) {
		this.repository = repository;
		this.handoffFreezeGate = handoffFreezeGate;
	}

	public WebsiteImplementationCandidate promote(
			UUID projectId,
			UUID agentExecutionId,
			String developerResultJson,
			String executionInputJson,
			Workspace workspace,
			FrozenHandoffSnapshot snapshot) {
		return repository.findByAgentExecutionId(agentExecutionId).orElseGet(() -> {
			if (!handoffFreezeGate.matchesCurrentState(workspace, snapshot)) {
				throw new RepositoryStateMismatchException(agentExecutionId);
			}

			WebsiteImplementationCandidate candidate =
					buildCandidate(projectId, agentExecutionId, developerResultJson, executionInputJson, snapshot);
			try {
				return repository.saveAndFlush(candidate);
			} catch (DataIntegrityViolationException raced) {
				return repository
						.findByAgentExecutionId(agentExecutionId)
						.orElseThrow(() -> raced);
			}
		});
	}

	private WebsiteImplementationCandidate buildCandidate(
			UUID projectId, UUID agentExecutionId, String developerResultJson, String executionInputJson, FrozenHandoffSnapshot snapshot) {
		JsonNode result = objectMapper.readTree(developerResultJson);
		JsonNode input = objectMapper.readTree(executionInputJson);

		return new WebsiteImplementationCandidate(
				projectId,
				agentExecutionId,
				result.path("targetDesign").path("designArtifactVersionRef").asString(null),
				result.path("targetDesign").path("proposalLocalRef").asString(null),
				input.path("technicalContext").path("runtimeProfileRef").asString(null),
				snapshot.snapshotId(),
				result.path("implementationSummary").asString(null),
				objectMapper.writeValueAsString(result.path("implementationAnchors")),
				objectMapper.writeValueAsString(result.path("functionalBindings")),
				objectMapper.writeValueAsString(result.path("unresolvedIssues")));
	}
}
