package ai.architech.backend.core.projectinput;

import ai.architech.backend.core.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects/{projectId}/inputs")
class ProjectInputController {

	private final ProjectRepository projectRepository;
	private final ProjectInputRepository projectInputRepository;

	ProjectInputController(ProjectRepository projectRepository, ProjectInputRepository projectInputRepository) {
		this.projectRepository = projectRepository;
		this.projectInputRepository = projectInputRepository;
	}

	@PostMapping
	ResponseEntity<ProjectInputResponse> submit(
			@PathVariable UUID projectId, @RequestBody SubmitProjectInputRequest request) {
		requireProjectExists(projectId);

		ProjectInput input = projectInputRepository.save(new ProjectInput(projectId, request.content()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ProjectInputResponse.from(input));
	}

	@GetMapping
	List<ProjectInputResponse> list(@PathVariable UUID projectId) {
		requireProjectExists(projectId);

		return projectInputRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
				.map(ProjectInputResponse::from)
				.toList();
	}

	private void requireProjectExists(UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with id " + projectId);
		}
	}
}
