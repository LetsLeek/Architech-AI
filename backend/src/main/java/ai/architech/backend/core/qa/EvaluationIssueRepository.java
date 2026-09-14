package ai.architech.backend.core.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationIssueRepository extends JpaRepository<EvaluationIssue, UUID> {

	List<EvaluationIssue> findByQaResultIdOrderByCreatedAtAsc(UUID qaResultId);
}
