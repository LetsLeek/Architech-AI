package ai.architech.backend.core.qa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceManifestRepository extends JpaRepository<EvidenceManifest, UUID> {

	Optional<EvidenceManifest> findByQaExecutionId(UUID qaExecutionId);
}
