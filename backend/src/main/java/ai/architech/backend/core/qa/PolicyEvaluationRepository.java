package ai.architech.backend.core.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyEvaluationRepository extends JpaRepository<PolicyEvaluation, UUID> {

	List<PolicyEvaluation> findByQaResultIdOrderByCreatedAtAsc(UUID qaResultId);
}
