package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.repository.DevelopmentBaseProvisioner;
import ai.architech.backend.core.repository.DevelopmentBaseRef;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fans one canonical {@code design-proposal-set} out into three isolated, sibling-blind Website
 * Developer executions and aggregates their outcomes (AIW-146). Composes entirely existing,
 * independently-tested infrastructure - {@link DevelopmentBaseProvisioner} for the shared base
 * and per-sibling isolated workspaces, {@link DeveloperExecutionInputAssembler} for the
 * already-proven single-proposal input assembly, {@link AgentExecution}/{@link
 * WebsiteImplementationCandidate} for execution/acceptance state - rather than introducing a
 * second way to do any of those things.
 *
 * <p><strong>Deliberately stops short of driving a sibling to completion</strong>: there is no
 * agentic tool-calling loop in this codebase yet (the still-unticketed "Track 2C" gap this
 * epic's own sequencing plan names) to actually turn an assembled {@code
 * developer-execution-input.v1} payload into a real model-driven implementation. {@link #start}
 * leaves every sibling {@code AgentExecution} at {@code RUNNING} once its workspace and input are
 * proven correct and isolated - advancing it to a terminal outcome is whatever eventually
 * implements that loop's job, not this orchestrator's. This is the same "prove what's real,
 * document what still needs unbuilt infrastructure" boundary already drawn in {@code
 * DeveloperResultValidator}/{@code AuthoritativeRunnerVerifier}'s own AIW-142/143 javadoc.
 */
@Component
public class InitialGenerationOrchestrator {

	static final String DEVELOPER_AGENT_ID = "developer-agent";
	static final int DEVELOPER_AGENT_VERSION = 1;
	private static final String DESIGN_PROPOSAL_SET_TYPE = "design-proposal-set";

	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final AgentExecutionRepository agentExecutionRepository;
	private final DevelopmentBaseProvisioner developmentBaseProvisioner;
	private final DeveloperExecutionInputAssembler developerExecutionInputAssembler;
	private final InitialGenerationBatchRepository batchRepository;
	private final InitialGenerationSlotRepository slotRepository;
	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final InitialGenerationProperties properties;
	private final ObjectMapper objectMapper = new ObjectMapper();

	InitialGenerationOrchestrator(
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			AgentExecutionRepository agentExecutionRepository,
			DevelopmentBaseProvisioner developmentBaseProvisioner,
			DeveloperExecutionInputAssembler developerExecutionInputAssembler,
			InitialGenerationBatchRepository batchRepository,
			InitialGenerationSlotRepository slotRepository,
			WebsiteImplementationCandidateRepository candidateRepository,
			InitialGenerationProperties properties) {
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.agentExecutionRepository = agentExecutionRepository;
		this.developmentBaseProvisioner = developmentBaseProvisioner;
		this.developerExecutionInputAssembler = developerExecutionInputAssembler;
		this.batchRepository = batchRepository;
		this.slotRepository = slotRepository;
		this.candidateRepository = candidateRepository;
		this.properties = properties;
	}

	/**
	 * Requires the project's canonical {@code design-proposal-set} to have exactly three
	 * proposals. Ensures the project's Development Base exists (idempotent), then creates one
	 * proposal-scoped {@link InitialGenerationSlot} per proposal, each with its own {@link
	 * AgentExecution}, its own workspace cloned from the exact same base commit, and its own
	 * assembled input containing only that one proposal.
	 */
	public InitialGenerationBatch start(UUID projectId, Path projectRepositoryRoot, Path workspacesRootDirectory) {
		ArtifactVersion proposalSetVersion = latestDesignProposalSetVersion(projectId);
		List<String> proposalLocalRefs = proposalLocalRefs(proposalSetVersion);
		if (proposalLocalRefs.size() != 3) {
			throw new IllegalStateException("design-proposal-set for project " + projectId
					+ " must have exactly 3 proposals to start initial generation, has " + proposalLocalRefs.size());
		}

		DevelopmentBaseRef base = developmentBaseProvisioner.ensureProvisioned(projectRepositoryRoot);
		InitialGenerationBatch batch =
				batchRepository.saveAndFlush(new InitialGenerationBatch(projectId, proposalSetVersion.getId()));

		for (String proposalLocalRef : proposalLocalRefs) {
			AgentExecution execution = createExecution(projectId, null, null);
			provisionAndAssemble(execution, projectId, proposalLocalRef, base, projectRepositoryRoot, workspacesRootDirectory, null);
			slotRepository.saveAndFlush(new InitialGenerationSlot(batch.getId(), proposalLocalRef, execution.getId()));
		}

		return batch;
	}

	/**
	 * Mechanically retries exactly the named slot's current execution, which must be {@code
	 * FAILED} or {@code ERROR} - a semantically {@code BLOCKED} sibling is never mechanically
	 * retried (retrying with the same inputs would likely reproduce the same blocker); call
	 * {@link #escalate} directly for that case instead, the "explicit workflow retry/escalation
	 * path" this ticket's own Aggregate Semantics section requires. Every other slot in the batch
	 * is untouched by this call - "one sibling failure cannot mutate or invalidate another
	 * successful Candidate" holds because nothing here ever reads or writes another slot's row.
	 *
	 * <p>Returns {@code true} if a new attempt was created and the slot advanced to it; returns
	 * {@code false} and escalates the whole batch instead once this slot's retry budget ({@code
	 * architech.developer.initial-generation.max-sibling-retries}) is exhausted - bounded by
	 * construction, never an unbounded loop.
	 */
	public boolean retrySlotOrEscalate(
			UUID batchId,
			String proposalLocalRef,
			String retryReasonCode,
			String failureEvidenceSummary,
			Path projectRepositoryRoot,
			Path workspacesRootDirectory) {
		InitialGenerationBatch batch = requireBatch(batchId);
		InitialGenerationSlot slot = requireSlot(batchId, proposalLocalRef);
		AgentExecution priorExecution = requireExecution(slot.getCurrentAgentExecutionId());

		if (priorExecution.getStatus() != AgentExecutionStatus.FAILED && priorExecution.getStatus() != AgentExecutionStatus.ERROR) {
			throw new IllegalStateException("Slot '" + proposalLocalRef + "' current execution is "
					+ priorExecution.getStatus() + " - only a FAILED or ERROR execution can be mechanically retried");
		}

		if (!slot.hasRetriesRemaining(properties.maxSiblingRetries())) {
			batch.escalate("Sibling '" + proposalLocalRef + "' exhausted its retry budget ("
					+ properties.maxSiblingRetries() + " retries)");
			batchRepository.saveAndFlush(batch);
			return false;
		}

		DevelopmentBaseRef base = developmentBaseProvisioner.ensureProvisioned(projectRepositoryRoot);
		AgentExecution retryExecution = createExecution(batch.getProjectId(), priorExecution.getId(), retryReasonCode);
		RetryContext retryContext =
				new RetryContext(priorExecution.getId().toString(), retryReasonCode, failureEvidenceSummary);
		provisionAndAssemble(
				retryExecution, batch.getProjectId(), proposalLocalRef, base, projectRepositoryRoot, workspacesRootDirectory, retryContext);

		boolean advanced = slot.retry(retryExecution.getId(), properties.maxSiblingRetries());
		slotRepository.saveAndFlush(slot);
		return advanced;
	}

	/** Explicit escalation path for a semantically BLOCKED sibling, or any other non-mechanical stop. */
	public void escalate(UUID batchId, String reason) {
		InitialGenerationBatch batch = requireBatch(batchId);
		batch.escalate(reason);
		batchRepository.saveAndFlush(batch);
	}

	/**
	 * Aggregate completion is only ever reported once every slot's current execution is {@code
	 * SUCCEEDED} with its own accepted Candidate - two successes and one still-pending/failed
	 * sibling reports {@code IN_PROGRESS}, never a partial "two-option" completion.
	 */
	public InitialGenerationBatchOutcome evaluate(UUID batchId) {
		InitialGenerationBatch batch = requireBatch(batchId);
		if (batch.getStatus() == InitialGenerationBatchStatus.ESCALATED) {
			return InitialGenerationBatchOutcome.escalated(batch.getEscalationReason());
		}

		List<InitialGenerationSlot> slots = slotRepository.findByBatchIdOrderByProposalLocalRefAsc(batchId);
		Map<String, UUID> candidateIdsByProposalLocalRef = new LinkedHashMap<>();
		for (InitialGenerationSlot slot : slots) {
			AgentExecution execution = requireExecution(slot.getCurrentAgentExecutionId());
			if (execution.getStatus() != AgentExecutionStatus.SUCCEEDED) {
				return InitialGenerationBatchOutcome.inProgress();
			}
			WebsiteImplementationCandidate candidate = candidateRepository
					.findByAgentExecutionId(execution.getId())
					.orElseThrow(() -> new IllegalStateException(
							"AgentExecution " + execution.getId() + " is SUCCEEDED but has no accepted Candidate"));
			candidateIdsByProposalLocalRef.put(slot.getProposalLocalRef(), candidate.getId());
		}

		if (batch.getStatus() == InitialGenerationBatchStatus.IN_PROGRESS) {
			batch.complete();
			batchRepository.saveAndFlush(batch);
		}
		return InitialGenerationBatchOutcome.complete(candidateIdsByProposalLocalRef);
	}

	private AgentExecution createExecution(UUID projectId, UUID retryOfExecutionId, String retryReasonCode) {
		AgentExecution execution = retryOfExecutionId == null
				? new AgentExecution(projectId, DEVELOPER_AGENT_ID, DEVELOPER_AGENT_VERSION)
				: new AgentExecution(projectId, DEVELOPER_AGENT_ID, DEVELOPER_AGENT_VERSION, retryOfExecutionId, retryReasonCode);
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private void provisionAndAssemble(
			AgentExecution execution,
			UUID projectId,
			String proposalLocalRef,
			DevelopmentBaseRef base,
			Path projectRepositoryRoot,
			Path workspacesRootDirectory,
			RetryContext retryContext) {
		Path workspaceRoot = workspacesRootDirectory.resolve(execution.getId().toString());
		// Proves every sibling shares the exact same base and gets its own isolated clone -
		// provisionWorkspace itself verifies the clone's HEAD matches `base` exactly.
		developmentBaseProvisioner.provisionWorkspace(projectRepositoryRoot, base, workspaceRoot);

		// Nothing consumes this payload yet (see class javadoc) - assembling it here is a real,
		// fail-fast proof that this sibling's input is buildable and contains only its own
		// proposal, ready for whatever eventually drives the execution to call it for real.
		if (retryContext == null) {
			developerExecutionInputAssembler.assemble(
					projectId, proposalLocalRef, base.commitSha(), properties.maxCorrectionCyclesPerSibling());
		} else {
			developerExecutionInputAssembler.assemble(
					projectId, proposalLocalRef, base.commitSha(), properties.maxCorrectionCyclesPerSibling(), retryContext);
		}
	}

	private ArtifactVersion latestDesignProposalSetVersion(UUID projectId) {
		Artifact artifact = artifactRepository
				.findByProjectIdAndType(projectId, DESIGN_PROPOSAL_SET_TYPE)
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.CANONICAL_ARTIFACT_NOT_FOUND,
						"No canonical '" + DESIGN_PROPOSAL_SET_TYPE + "' artifact exists yet for project " + projectId));
		return artifactVersionRepository
				.findTopByArtifactIdOrderByVersionNumberDesc(artifact.getId())
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.CANONICAL_ARTIFACT_NOT_FOUND,
						"Artifact '" + DESIGN_PROPOSAL_SET_TYPE + "' for project " + projectId + " has no version yet"));
	}

	private List<String> proposalLocalRefs(ArtifactVersion proposalSetVersion) {
		JsonNode content = objectMapper.readTree(proposalSetVersion.getContent());
		List<String> refs = new ArrayList<>();
		for (JsonNode proposal : content.path("proposals")) {
			refs.add(proposal.path("localRef").asString());
		}
		return refs;
	}

	private InitialGenerationBatch requireBatch(UUID batchId) {
		return batchRepository.findById(batchId).orElseThrow(() -> new NoSuchElementException("No InitialGenerationBatch with id " + batchId));
	}

	private InitialGenerationSlot requireSlot(UUID batchId, String proposalLocalRef) {
		return slotRepository
				.findByBatchIdAndProposalLocalRef(batchId, proposalLocalRef)
				.orElseThrow(() -> new NoSuchElementException(
						"No slot '" + proposalLocalRef + "' in InitialGenerationBatch " + batchId));
	}

	private AgentExecution requireExecution(UUID agentExecutionId) {
		return agentExecutionRepository
				.findById(agentExecutionId)
				.orElseThrow(() -> new NoSuchElementException("No AgentExecution with id " + agentExecutionId));
	}
}
