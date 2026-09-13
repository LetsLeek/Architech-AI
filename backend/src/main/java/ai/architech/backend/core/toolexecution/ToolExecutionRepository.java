package ai.architech.backend.core.toolexecution;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolExecutionRepository extends JpaRepository<ToolExecution, UUID> {

	List<ToolExecution> findByAgentExecutionIdOrderByStartedAtAsc(UUID agentExecutionId);
}
