package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.repository.DevelopmentBaseProvisioner;
import ai.architech.backend.core.repository.DevelopmentBaseRef;
import ai.architech.backend.core.runner.DeveloperToolLoopOrchestrator;
import ai.architech.backend.core.runner.DeveloperToolLoopResult;
import ai.architech.backend.core.sandbox.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives all three A/B/C sibling Website Developer executions from a canonical
 * {@code design-proposal-set} through to completion for real (AIW-212) - the missing "Track 2C"
 * connective layer {@link InitialGenerationOrchestrator}'s own class javadoc names as unbuilt
 * infrastructure: {@code start} fans a batch out and leaves every sibling {@code AgentExecution}
 * at {@code RUNNING}, and nothing before this ticket ever called {@link
 * DeveloperToolLoopOrchestrator#run} to actually advance one to a terminal status.
 *
 * <p><b>Deliberately does not call {@code InitialGenerationOrchestrator} at all</b> - a real
 * architecture mismatch discovered while building this ticket, not a design choice: {@code
 * InitialGenerationOrchestrator.start} creates its own {@code AgentExecution} per sibling up
 * front and records it on an {@code InitialGenerationSlot}, but {@link
 * DeveloperToolLoopOrchestrator#run} always creates a brand-new {@code AgentExecution} of its own
 * internally and has no way to drive an already-existing one. Chaining the two as originally
 * planned would silently orphan {@code start}'s own executions (forever stuck at {@code RUNNING})
 * while a second, disconnected execution actually completes - {@code
 * InitialGenerationOrchestrator.evaluate} would then never observe a slot's real outcome. Rather
 * than modify either already-tested class to reconcile this, this class independently reuses the
 * same lower-level building blocks {@code start} itself composes ({@link
 * DevelopmentBaseProvisioner}, {@link DeveloperExecutionInputAssembler}, the canonical
 * {@code design-proposal-set} lookup) and calls {@link DeveloperToolLoopOrchestrator#run}
 * directly, which already owns its own complete execution lifecycle end to end. {@code
 * InitialGenerationOrchestrator}'s own batch/slot/retry/escalate machinery is therefore not
 * exercised by this path at all - reconciling the two (so a batch's own slots reflect what this
 * class actually drives) is real, separate follow-up work, not solved here.
 *
 * <p>Runs the three siblings <strong>sequentially</strong>, not in parallel - the simplest
 * correct thing for this first version, with no new async/concurrency infrastructure. One
 * sibling's failure (an infrastructure exception before/during provisioning or input assembly, or
 * a terminal non-{@code SUCCEEDED} execution outcome) never prevents the other two from running -
 * each sibling's own attempt is wrapped independently.
 */
@Component
public class WebsiteGenerationDrivingService {

	private static final Logger log = LoggerFactory.getLogger(WebsiteGenerationDrivingService.class);

	private static final String DESIGN_PROPOSAL_SET_TYPE = "design-proposal-set";

	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final DevelopmentBaseProvisioner developmentBaseProvisioner;
	private final DeveloperExecutionInputAssembler developerExecutionInputAssembler;
	private final DeveloperToolLoopOrchestrator developerToolLoopOrchestrator;
	private final QaTriggerService qaTriggerService;
	private final InitialGenerationProperties properties;
	private final ObjectMapper objectMapper;

	WebsiteGenerationDrivingService(
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			DevelopmentBaseProvisioner developmentBaseProvisioner,
			DeveloperExecutionInputAssembler developerExecutionInputAssembler,
			DeveloperToolLoopOrchestrator developerToolLoopOrchestrator,
			QaTriggerService qaTriggerService,
			InitialGenerationProperties properties,
			ObjectMapper objectMapper) {
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.developmentBaseProvisioner = developmentBaseProvisioner;
		this.developerExecutionInputAssembler = developerExecutionInputAssembler;
		this.developerToolLoopOrchestrator = developerToolLoopOrchestrator;
		this.qaTriggerService = qaTriggerService;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	/**
	 * Requires the project's canonical {@code design-proposal-set} to have exactly three
	 * proposals - the same precondition {@code InitialGenerationOrchestrator.start} itself
	 * enforces. {@code projectRepositoryRoot}/{@code workspacesRootDirectory} are local
	 * filesystem paths - see {@link DeveloperGenerationController}'s own javadoc for why that is
	 * today's accepted V1 scope, not an oversight.
	 */
	public WebsiteGenerationOutcome generate(UUID projectId, Path projectRepositoryRoot, Path workspacesRootDirectory) {
		ArtifactVersion proposalSetVersion = latestDesignProposalSetVersion(projectId);
		List<String> proposalLocalRefs = proposalLocalRefs(proposalSetVersion);
		if (proposalLocalRefs.size() != 3) {
			throw new IllegalStateException("design-proposal-set for project " + projectId
					+ " must have exactly 3 proposals to start website generation, has " + proposalLocalRefs.size());
		}

		DevelopmentBaseRef base = developmentBaseProvisioner.ensureProvisioned(projectRepositoryRoot);

		List<WebsiteGenerationSiblingOutcome> outcomes = new ArrayList<>();
		for (String proposalLocalRef : proposalLocalRefs) {
			outcomes.add(driveSibling(projectId, proposalLocalRef, base, projectRepositoryRoot, workspacesRootDirectory));
		}
		return new WebsiteGenerationOutcome(outcomes);
	}

	private WebsiteGenerationSiblingOutcome driveSibling(
			UUID projectId,
			String proposalLocalRef,
			DevelopmentBaseRef base,
			Path projectRepositoryRoot,
			Path workspacesRootDirectory) {
		try {
			String executionInputJson = developerExecutionInputAssembler.assemble(
					projectId, proposalLocalRef, base.commitSha(), properties.maxCorrectionCyclesPerSibling());

			Path workspaceRoot = workspacesRootDirectory.resolve(proposalLocalRef);
			developmentBaseProvisioner.provisionWorkspace(projectRepositoryRoot, base, workspaceRoot);
			Workspace workspace = new Workspace(workspaceRoot);

			DeveloperToolLoopResult result = developerToolLoopOrchestrator.run(projectId, executionInputJson, workspace);
			if (result.candidate() != null) {
				triggerQa(projectId, proposalLocalRef, result.candidate());
			}
			return new WebsiteGenerationSiblingOutcome(proposalLocalRef, result.execution(), result.candidate(), null);
		} catch (RuntimeException e) {
			return new WebsiteGenerationSiblingOutcome(proposalLocalRef, null, null, e.getMessage());
		}
	}

	/**
	 * AIW-213's own hook point: fires the real QA execution trigger the moment Developer-
	 * Candidate-Acceptance has happened for real (that acceptance already ran inside {@link
	 * DeveloperToolLoopOrchestrator#run} by the time {@code result.candidate()} is non-null - see
	 * this class's own javadoc). Best-effort/non-fatal by design: {@link
	 * QaTriggerService#triggerFullReleaseQa} already never throws, but this is still wrapped
	 * defensively so a QA-side surprise can never turn a successful Developer sibling into a
	 * failed one. {@link WebsiteGenerationSiblingOutcome}'s own shape is deliberately left
	 * unchanged by this ticket - surfacing QA outcome there is real, separate follow-up scope; for
	 * now a warning log line is this V1's own "never silently vanish without a trace" guarantee.
	 */
	private void triggerQa(UUID projectId, String proposalLocalRef, WebsiteImplementationCandidate candidate) {
		try {
			QaTriggerOutcome outcome = qaTriggerService.triggerFullReleaseQa(projectId, candidate);
			if (!outcome.succeeded()) {
				log.warn(
						"QA trigger did not produce a QaResult for project {} sibling {} candidate {}: qaIssues={} failureMessage={}",
						projectId,
						proposalLocalRef,
						candidate.getId(),
						outcome.qaIssues(),
						outcome.failureMessage());
			}
		} catch (RuntimeException e) {
			log.warn(
					"QA trigger threw unexpectedly for project {} sibling {} candidate {}",
					projectId,
					proposalLocalRef,
					candidate.getId(),
					e);
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
			String localRef = proposal.path("localRef").asString(null);
			if (localRef != null) {
				refs.add(localRef);
			}
		}
		return refs;
	}
}
