package ai.architech.backend.core.projectinput;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectInputRepository extends JpaRepository<ProjectInput, UUID> {

	List<ProjectInput> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
