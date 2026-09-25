package ai.architech.backend.core.candidate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteImplementationCandidateRepository extends JpaRepository<WebsiteImplementationCandidate, UUID> {

	Optional<WebsiteImplementationCandidate> findByAgentExecutionId(UUID agentExecutionId);

	List<WebsiteImplementationCandidate> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
