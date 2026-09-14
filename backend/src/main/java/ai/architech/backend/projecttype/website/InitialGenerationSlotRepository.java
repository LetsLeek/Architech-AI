package ai.architech.backend.projecttype.website;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InitialGenerationSlotRepository extends JpaRepository<InitialGenerationSlot, UUID> {

	List<InitialGenerationSlot> findByBatchIdOrderByProposalLocalRefAsc(UUID batchId);

	Optional<InitialGenerationSlot> findByBatchIdAndProposalLocalRef(UUID batchId, String proposalLocalRef);
}
