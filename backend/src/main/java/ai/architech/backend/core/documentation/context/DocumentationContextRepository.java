package ai.architech.backend.core.documentation.context;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentationContextRepository extends JpaRepository<DocumentationContext, UUID> {}
