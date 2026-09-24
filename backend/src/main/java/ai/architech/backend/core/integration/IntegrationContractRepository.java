package ai.architech.backend.core.integration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationContractRepository extends JpaRepository<IntegrationContract, UUID> {

	Optional<IntegrationContract> findByProjectIdAndContractRef(UUID projectId, String contractRef);
}
