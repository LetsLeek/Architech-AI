package ai.architech.backend.core.artifact;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateOutputRepository extends JpaRepository<CandidateOutput, UUID> {

	List<CandidateOutput> findByAgentExecutionId(UUID agentExecutionId);
}
