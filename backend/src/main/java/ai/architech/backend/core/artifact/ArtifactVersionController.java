package ai.architech.backend.core.artifact;

import ai.architech.backend.core.project.ProjectRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the latest canonical {@link ArtifactVersion} of a project's artifact, by type.
 * Deliberately generic on {@code type} (a plain path segment, same as {@link Artifact#getType()}
 * itself) rather than one endpoint per Requirements artifact type - Core doesn't know or care
 * that "customer-profile"/"website-requirements" are the only types that exist today
 * ({@link RequirementsOutputPersister} is where that's hardcoded); serving whichever
 * {@link ArtifactVersion} rows actually exist for a project needs no Website-specific
 * knowledge at all. AIW-57/58's frontend is where the Website-specific shape gets interpreted.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/artifacts/{type}")
class ArtifactVersionController {

	private final ProjectRepository projectRepository;
	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final ObjectMapper objectMapper;

	ArtifactVersionController(
			ProjectRepository projectRepository,
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			ObjectMapper objectMapper) {
		this.projectRepository = projectRepository;
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.objectMapper = objectMapper;
	}

	@GetMapping
	ArtifactVersionResponse getLatest(@PathVariable UUID projectId, @PathVariable String type) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with id " + projectId);
		}

		Artifact artifact = artifactRepository
				.findByProjectIdAndType(projectId, type)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "No canonical '" + type + "' artifact exists yet for this project"));

		ArtifactVersion latest = artifactVersionRepository
				.findTopByArtifactIdOrderByVersionNumberDesc(artifact.getId())
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "No canonical '" + type + "' artifact exists yet for this project"));

		JsonNode content = objectMapper.readTree(latest.getContent());
		return new ArtifactVersionResponse(
				artifact.getId(), artifact.getType(), latest.getVersionNumber(), content, latest.getCreatedAt());
	}
}
