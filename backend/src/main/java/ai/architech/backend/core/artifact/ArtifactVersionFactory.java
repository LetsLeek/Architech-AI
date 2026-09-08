package ai.architech.backend.core.artifact;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Creates a new {@link ArtifactVersion} under the (project, type)'s {@link Artifact},
 * creating that Artifact the first time this type is ever produced for the project.
 * Pure persistence mechanics only - deciding whether content is actually valid enough to
 * become a canonical version is the caller's responsibility (see AIW-44/45), not this
 * factory's; it will happily persist whatever content it's given.
 */
@Component
public class ArtifactVersionFactory {

	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;

	ArtifactVersionFactory(ArtifactRepository artifactRepository, ArtifactVersionRepository artifactVersionRepository) {
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
	}

	public ArtifactVersion createNextVersion(UUID projectId, String type, UUID agentExecutionId, String content) {
		Artifact artifact = artifactRepository
				.findByProjectIdAndType(projectId, type)
				.orElseGet(() -> artifactRepository.save(new Artifact(projectId, type)));

		int nextVersionNumber = artifactVersionRepository.findByArtifactIdOrderByVersionNumberDesc(artifact.getId()).stream()
				.findFirst()
				.map(v -> v.getVersionNumber() + 1)
				.orElse(1);

		return artifactVersionRepository.save(
				new ArtifactVersion(artifact.getId(), nextVersionNumber, agentExecutionId, content));
	}
}
