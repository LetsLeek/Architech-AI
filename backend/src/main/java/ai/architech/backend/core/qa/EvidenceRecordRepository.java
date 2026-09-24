package ai.architech.backend.core.qa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, UUID> {}
