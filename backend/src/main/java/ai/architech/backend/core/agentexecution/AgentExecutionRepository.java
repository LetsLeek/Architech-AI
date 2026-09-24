package ai.architech.backend.core.agentexecution;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {

	boolean existsByProjectIdAndStatus(UUID projectId, AgentExecutionStatus status);

	/** Every execution that retries {@code executionId}, oldest first - AIW-149's own auditability requirement. */
	List<AgentExecution> findByRetryOfExecutionIdOrderByCreatedAtAsc(UUID executionId);

	/** The most recent attempt of a given agent for a project, so a caller can report current run state (AIW-163) without already holding a specific execution id. */
	Optional<AgentExecution> findTopByProjectIdAndAgentIdOrderByCreatedAtDesc(UUID projectId, String agentId);

	/** {@code COALESCE} so "no executions yet" and "no real cost recorded yet" both read as zero, never {@code null} - {@link ai.architech.backend.core.ai.AiUsageBudgetGuard} compares this directly against a configured limit. */
	@Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM AgentExecution e WHERE e.agentId = :agentId")
	BigDecimal sumCostUsdByAgentId(@Param("agentId") String agentId);

	@Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM AgentExecution e WHERE e.projectId = :projectId")
	BigDecimal sumCostUsdByProjectId(@Param("projectId") UUID projectId);
}
