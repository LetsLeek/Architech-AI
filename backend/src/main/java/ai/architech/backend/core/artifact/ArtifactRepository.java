package ai.architech.backend.core.artifact;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

	Optional<Artifact> findByProjectIdAndType(UUID projectId, String type);
}
