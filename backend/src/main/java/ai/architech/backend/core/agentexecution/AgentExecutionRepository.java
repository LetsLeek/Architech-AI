package ai.architech.backend.core.agentexecution;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {

	boolean existsByProjectIdAndStatus(UUID projectId, AgentExecutionStatus status);
}
