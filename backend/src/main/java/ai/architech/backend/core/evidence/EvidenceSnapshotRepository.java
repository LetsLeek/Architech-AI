package ai.architech.backend.core.evidence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceSnapshotRepository extends JpaRepository<EvidenceSnapshot, UUID> {}
