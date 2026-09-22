package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Designer readiness/generation-state API (AIW-163) - lets the frontend determine
 * deterministically whether Designer Agent V1 can be invoked and what its current generation
 * state is, without guessing from unrelated fields or reconstructing the precondition itself
 * (that precondition is exactly {@link DesignerAgentRunner#run}'s own canonical-input check -
 * this controller mirrors it as a read, never re-deriving it a different way).
 *
 * <p>Composes only already-existing canonical state ({@code Artifact}/{@code ArtifactVersion}
 * for {@code customer-profile}/{@code website-requirements}/{@code design-proposal-set}, {@code
 * AgentExecution} for run state) - no Designer-specific source of truth is introduced.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/design-readiness")
class DesignerReadinessController {

	private final ProjectRepository projectRepository;
	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final AgentExecutionRepository agentExecutionRepository;

	DesignerReadinessController(
			ProjectRepository projectRepository,
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			AgentExecutionRepository agentExecutionRepository) {
		this.projectRepository = projectRepository;
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.agentExecutionRepository = agentExecutionRepository;
	}

	@GetMapping
	ResponseEntity<DesignerReadinessResponse> get(@PathVariable UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
		}

		boolean customerProfileExists =
				artifactRepository.findByProjectIdAndType(projectId, DesignerAgentRunner.CUSTOMER_PROFILE_TYPE).isPresent();
		boolean websiteRequirementsExists = artifactRepository
				.findByProjectIdAndType(projectId, DesignerAgentRunner.WEBSITE_REQUIREMENTS_TYPE)
				.isPresent();

		Optional<AgentExecution> latestExecution =
				agentExecutionRepository.findTopByProjectIdAndAgentIdOrderByCreatedAtDesc(projectId, DesignerAgentRunner.AGENT_ID);

		Optional<Artifact> designProposalSetArtifact =
				artifactRepository.findByProjectIdAndType(projectId, DesignerAgentRunner.DESIGN_PROPOSAL_SET_TYPE);
		Optional<ArtifactVersion> latestDesignProposalSetVersion =
				designProposalSetArtifact.flatMap(artifact -> artifactVersionRepository.findTopByArtifactIdOrderByVersionNumberDesc(artifact.getId()));

		return ResponseEntity.ok(DesignerReadinessResponse.from(
				customerProfileExists, websiteRequirementsExists, latestExecution, designProposalSetArtifact, latestDesignProposalSetVersion));
	}
}
