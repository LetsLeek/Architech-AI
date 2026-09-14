package ai.architech.backend.core.verification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunnerVerificationGateRecordRepository extends JpaRepository<RunnerVerificationGateRecord, UUID> {

	List<RunnerVerificationGateRecord> findByRunnerVerificationRunIdOrderByGateOrderAsc(UUID runnerVerificationRunId);
}
