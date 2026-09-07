package ai.architech.backend.core.projectinput;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileProjectInputRepository extends JpaRepository<FileProjectInput, UUID> {

	List<FileProjectInput> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
