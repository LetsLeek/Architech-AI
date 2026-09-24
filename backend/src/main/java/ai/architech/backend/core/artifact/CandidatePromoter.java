package ai.architech.backend.core.artifact;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only path from a {@link CandidateOutput} to a canonical {@link ArtifactVersion}.
 * Deliberately does no validation itself - it promotes whatever candidate it's given,
 * exactly as recorded. Callers (AIW-45 and later, once real validators exist per AIW-47..49)
 * are responsible for only ever calling this after a candidate has actually passed
 * validation; this class has no way to check that itself.
 */
@Component
public class CandidatePromoter {

	private final ArtifactVersionFactory artifactVersionFactory;

	CandidatePromoter(ArtifactVersionFactory artifactVersionFactory) {
		this.artifactVersionFactory = artifactVersionFactory;
	}

	public ArtifactVersion promote(UUID projectId, CandidateOutput candidate) {
		return artifactVersionFactory.createNextVersion(
				projectId, candidate.getArtifactType(), candidate.getAgentExecutionId(), candidate.getContent());
	}
}
