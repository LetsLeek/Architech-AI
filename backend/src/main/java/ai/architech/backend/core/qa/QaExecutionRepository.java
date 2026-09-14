package ai.architech.backend.core.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaExecutionRepository extends JpaRepository<QaExecution, UUID> {

	List<QaExecution> findByTestedCandidateIdOrderByCreatedAtAsc(UUID testedCandidateId);
}
