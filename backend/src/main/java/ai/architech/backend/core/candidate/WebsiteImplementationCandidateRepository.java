package ai.architech.backend.core.candidate;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteImplementationCandidateRepository extends JpaRepository<WebsiteImplementationCandidate, UUID> {

	Optional<WebsiteImplementationCandidate> findByAgentExecutionId(UUID agentExecutionId);
}
