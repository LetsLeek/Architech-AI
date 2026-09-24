package ai.architech.backend.core.documentation.rendering;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentationRenderRepository extends JpaRepository<DocumentationRender, UUID> {

	List<DocumentationRender> findByPackageVersionIdOrderByCreatedAtAsc(UUID packageVersionId);
}
