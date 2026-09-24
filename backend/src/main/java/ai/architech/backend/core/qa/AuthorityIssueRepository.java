package ai.architech.backend.core.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorityIssueRepository extends JpaRepository<AuthorityIssue, UUID> {

	List<AuthorityIssue> findByQaResultIdOrderByCreatedAtAsc(UUID qaResultId);
}
