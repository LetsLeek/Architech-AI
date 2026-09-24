package ai.architech.backend.projecttype.website;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InitialGenerationBatchRepository extends JpaRepository<InitialGenerationBatch, UUID> {}
