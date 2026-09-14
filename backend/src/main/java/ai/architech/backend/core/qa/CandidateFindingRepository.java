package ai.architech.backend.core.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateFindingRepository extends JpaRepository<CandidateFinding, UUID> {

	List<CandidateFinding> findByQaResultIdOrderByCreatedAtAsc(UUID qaResultId);

	List<CandidateFinding> findByTestedCandidateIdOrderByCreatedAtAsc(UUID testedCandidateId);
}
