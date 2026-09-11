package ai.architech.backend.core.evidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRefRepository extends JpaRepository<SourceRef, UUID> {

	List<SourceRef> findByEvidenceSnapshotId(UUID evidenceSnapshotId);

	Optional<SourceRef> findByEvidenceSnapshotIdAndRef(UUID evidenceSnapshotId, String ref);
}
