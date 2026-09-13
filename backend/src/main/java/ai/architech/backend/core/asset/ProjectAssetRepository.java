package ai.architech.backend.core.asset;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAssetRepository extends JpaRepository<ProjectAsset, UUID> {

	Optional<ProjectAsset> findByProjectIdAndAssetRef(UUID projectId, String assetRef);
}
