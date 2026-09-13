package ai.architech.backend.core.asset;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * AIW-152's deterministic, project-scoped asset resolution boundary. Every lookup is scoped by
 * {@code (projectId, assetRef)} together (delegating straight to
 * {@link ProjectAssetRepository#findByProjectIdAndAssetRef}) - there is no method here, or
 * anywhere in this class, that resolves an asset by ref alone, so a real asset belonging to a
 * different project is structurally unreachable regardless of how its ref string looks.
 *
 * <p>There is also no method here that fetches from any external source (a URL, a stock-image
 * API, a web search) - the only data this class ever returns is a {@link ProjectAsset} row
 * already persisted for the exact project asked about. "Missing assets never trigger automatic
 * external sourcing or invented substitutions" is therefore structural, not a discipline this
 * class has to remember: there is no capability here to source anything external at all.
 *
 * <p>This class deliberately does not decide {@code MISSING_UPSTREAM_INFORMATION} vs.
 * {@code DeveloperBlocker} - that is the Developer Agent's own judgment call (RULE.md's own
 * traceability rules), made with the {@link Optional}/exception outcome below as its input, not
 * a decision this Core resolver can make on the agent's behalf.
 */
@Component
public class ProjectAssetResolver {

	private final ProjectAssetRepository projectAssetRepository;

	ProjectAssetResolver(ProjectAssetRepository projectAssetRepository) {
		this.projectAssetRepository = projectAssetRepository;
	}

	/** For an asset the target Proposal requires to exist for meaningful completion. */
	public ProjectAsset resolveRequired(UUID projectId, String assetRef) {
		return resolveOptional(projectId, assetRef)
				.orElseThrow(() -> new MissingProjectAssetException(projectId, assetRef));
	}

	/** For an asset that may legitimately be absent - the caller decides what an empty result means. */
	public Optional<ProjectAsset> resolveOptional(UUID projectId, String assetRef) {
		return projectAssetRepository.findByProjectIdAndAssetRef(projectId, assetRef);
	}
}
