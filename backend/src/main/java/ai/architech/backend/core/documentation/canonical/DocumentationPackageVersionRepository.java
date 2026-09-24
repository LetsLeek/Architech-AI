package ai.architech.backend.core.documentation.canonical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentationPackageVersionRepository extends JpaRepository<DocumentationPackageVersion, UUID> {

	Optional<DocumentationPackageVersion> findByDocumentationLineIdAndIdempotencyKey(UUID documentationLineId, String idempotencyKey);

	List<DocumentationPackageVersion> findByDocumentationLineIdOrderByRevisionAsc(UUID documentationLineId);
}
