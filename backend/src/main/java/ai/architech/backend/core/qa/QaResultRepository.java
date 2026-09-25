package ai.architech.backend.core.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaResultRepository extends JpaRepository<QaResult, UUID> {

	List<QaResult> findByQaExecutionIdOrderByCreatedAtAsc(UUID qaExecutionId);

	List<QaResult> findByTestedCandidateIdOrderByCreatedAtAsc(UUID testedCandidateId);

	List<QaResult> findByTestedCandidateIdInOrderByCreatedAtDesc(List<UUID> testedCandidateIds);
}
