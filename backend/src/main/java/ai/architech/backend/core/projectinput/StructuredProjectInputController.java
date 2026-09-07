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
@RequestMapping("/api/projects/{projectId}/structured-inputs")
class StructuredProjectInputController {

	private final ProjectRepository projectRepository;
	private final StructuredProjectInputRepository structuredProjectInputRepository;

	StructuredProjectInputController(
			ProjectRepository projectRepository,
			StructuredProjectInputRepository structuredProjectInputRepository) {
		this.projectRepository = projectRepository;
		this.structuredProjectInputRepository = structuredProjectInputRepository;
	}

	@PostMapping
	ResponseEntity<StructuredProjectInputResponse> submit(
			@PathVariable UUID projectId, @RequestBody SubmitStructuredProjectInputRequest request) {
		requireProjectExists(projectId);

		StructuredProjectInput input = structuredProjectInputRepository.save(
				new StructuredProjectInput(projectId, request.fields()));
		return ResponseEntity.status(HttpStatus.CREATED).body(StructuredProjectInputResponse.from(input));
	}

	@GetMapping
	List<StructuredProjectInputResponse> list(@PathVariable UUID projectId) {
		requireProjectExists(projectId);

		return structuredProjectInputRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
				.map(StructuredProjectInputResponse::from)
				.toList();
	}

	private void requireProjectExists(UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with id " + projectId);
		}
	}
}
