package ai.architech.backend.core.validation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperResultValidationRecordRepository extends JpaRepository<DeveloperResultValidationRecord, UUID> {

	List<DeveloperResultValidationRecord> findByAgentExecutionIdOrderByCreatedAtAsc(UUID agentExecutionId);
}
