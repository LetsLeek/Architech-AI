package ai.architech.backend.core.qa.remediation;

import ai.architech.backend.core.qa.profiles.QaProfileType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaRemediationBudgetRepository extends JpaRepository<QaRemediationBudget, UUID> {

	Optional<QaRemediationBudget> findByProjectIdAndVariantLineageRefAndStage(UUID projectId, String variantLineageRef, QaProfileType stage);
}
