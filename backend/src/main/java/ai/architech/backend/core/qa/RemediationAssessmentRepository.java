package ai.architech.backend.core.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemediationAssessmentRepository extends JpaRepository<RemediationAssessment, UUID> {

	List<RemediationAssessment> findByPreviousFindingIdOrderByCreatedAtAsc(UUID previousFindingId);

	List<RemediationAssessment> findByQaResultIdOrderByCreatedAtAsc(UUID qaResultId);
}
