package ai.architech.backend.core.projectinput;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StructuredProjectInputRepository extends JpaRepository<StructuredProjectInput, UUID> {

	List<StructuredProjectInput> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

	boolean existsByProjectId(UUID projectId);
}
