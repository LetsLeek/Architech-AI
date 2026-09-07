package ai.architech.backend.core.project;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic Project API: any projectType could in principle be stored, but only the types in
 * {@link #SUPPORTED_PROJECT_TYPES} are currently accepted. Extend that set as new project
 * types launch - Core itself never needs to change for that.
 */
@RestController
@RequestMapping("/api/projects")
class ProjectController {

	private static final Set<String> SUPPORTED_PROJECT_TYPES = Set.of("website");

	private final ProjectRepository projectRepository;

	ProjectController(ProjectRepository projectRepository) {
		this.projectRepository = projectRepository;
	}

	@PostMapping
	ResponseEntity<ProjectResponse> create(@RequestBody CreateProjectRequest request) {
		if (!SUPPORTED_PROJECT_TYPES.contains(request.projectType())) {
			throw new UnsupportedProjectTypeException(request.projectType());
		}

		Project project = projectRepository.save(new Project(request.projectType()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
	}

	@GetMapping("/{id}")
	ResponseEntity<ProjectResponse> get(@PathVariable UUID id) {
		return projectRepository
				.findById(id)
				.map(ProjectResponse::from)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
