package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers Website Developer generation for a project (AIW-212) - the first real caller anywhere
 * in this codebase of the A/B/C sibling generation path, same shape/guard reasoning as {@link
 * DesignProposalGenerationController}.
 *
 * <p><b>Known, accepted V1 limitation - local filesystem workspace storage only</b>, confirmed
 * with the user this session: {@code projectRepositoryRoot}/{@code workspacesRootDirectory} are
 * fixed paths under the running backend instance's own temp directory. This is exactly the scope
 * {@link ai.architech.backend.core.repository.DevelopmentBaseProvisioner}'s own class javadoc
 * already accepts for V1 ("provisions a local repository only... wiring a real remote host is a
 * separate, explicitly deferred concern") - it works correctly against today's real DEV
 * environment (confirmed this session to be exactly one Container App replica) but does not
 * survive a restart or horizontal scaling. A real remote/persistent workspace store is real,
 * separate follow-up work, not solved here.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/website-generation")
class DeveloperGenerationController {

	private final ProjectRepository projectRepository;
	private final AgentExecutionRepository agentExecutionRepository;
	private final WebsiteGenerationDrivingService drivingService;

	DeveloperGenerationController(
			ProjectRepository projectRepository,
			AgentExecutionRepository agentExecutionRepository,
			WebsiteGenerationDrivingService drivingService) {
		this.projectRepository = projectRepository;
		this.agentExecutionRepository = agentExecutionRepository;
		this.drivingService = drivingService;
	}

	@PostMapping
	ResponseEntity<WebsiteGenerationResponse> start(@PathVariable UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
		}
		if (agentExecutionRepository.existsByProjectIdAndStatus(projectId, AgentExecutionStatus.RUNNING)) {
			throw new ApplicationException(
					ErrorCode.WEBSITE_GENERATION_ALREADY_RUNNING, "A website generation is already running for this project");
		}

		Path root = Path.of(System.getProperty("java.io.tmpdir"), "architech-workspaces", projectId.toString());
		Path projectRepositoryRoot = root.resolve("repo");
		Path workspacesRootDirectory = root.resolve("workspaces");

		WebsiteGenerationOutcome outcome = drivingService.generate(projectId, projectRepositoryRoot, workspacesRootDirectory);
		return ResponseEntity.status(HttpStatus.CREATED).body(WebsiteGenerationResponse.from(outcome));
	}
}
