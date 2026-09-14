package ai.architech.backend.core.verification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunnerVerificationRunRepository extends JpaRepository<RunnerVerificationRun, UUID> {

	List<RunnerVerificationRun> findByAgentExecutionIdOrderByCreatedAtAsc(UUID agentExecutionId);
}
