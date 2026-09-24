package ai.architech.backend.projecttype.website;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonReadinessSlotRepository extends JpaRepository<ComparisonReadinessSlot, UUID> {

	List<ComparisonReadinessSlot> findByBarrierIdOrderByCreatedAtAsc(UUID barrierId);
}
