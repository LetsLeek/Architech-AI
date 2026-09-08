package ai.architech.backend.core.artifact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, UUID> {

	List<ArtifactVersion> findByArtifactIdOrderByVersionNumberDesc(UUID artifactId);

	List<ArtifactVersion> findByAgentExecutionId(UUID agentExecutionId);

	Optional<ArtifactVersion> findByArtifactIdAndVersionNumber(UUID artifactId, int versionNumber);

	Optional<ArtifactVersion> findTopByArtifactIdOrderByVersionNumberDesc(UUID artifactId);
}
