package ai.architech.backend.core.documentation.canonical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentationLineRepository extends JpaRepository<DocumentationLine, UUID> {

	Optional<DocumentationLine> findByProjectIdAndProfileRefAndLocale(UUID projectId, String profileRef, String locale);

	List<DocumentationLine> findByProjectId(UUID projectId);
}
