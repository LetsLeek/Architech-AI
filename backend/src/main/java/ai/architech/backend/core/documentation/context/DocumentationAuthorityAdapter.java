package ai.architech.backend.core.documentation.context;

import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.qa.QaResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Translates already-persisted upstream rows into the {@code authoritySnapshot}/{@code qaState}
 * shapes {@code documentation-context.schema.json} requires (AIW-189) - the Documentation
 * analogue of {@code DeveloperExecutionInputAssembler}'s own "materialize already-validated
 * content, never an AI-generated summary" idiom.
 *
 * <p>{@code websiteRequirementsRef}/{@code customerProfileRef} resolve to the project's current
 * canonical {@code website-requirements}/{@code customer-profile} artifact version, not the exact
 * version the Candidate's own originating Developer execution actually consumed -
 * {@link WebsiteImplementationCandidate} does not store that link (the same already-documented gap
 * {@code DeveloperExecutionInputAssembler#assembleForRemediation}'s own javadoc names), so
 * byte-exact Candidate&rarr;Requirements lineage cannot be verified today. Not silently worked
 * around: this is why {@link #buildAuthoritySnapshot} is only ever safe to call once {@link
 * ai.architech.backend.core.validation.DocumentationContextPreflightValidator} has already
 * confirmed the project has a current canonical version of every profile-required root - this
 * class itself performs no defensive re-checking of that.
 */
@Component
public class DocumentationAuthorityAdapter {

	static final String CUSTOMER_PROFILE_TYPE = "customer-profile";
	static final String WEBSITE_REQUIREMENTS_TYPE = "website-requirements";

	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;

	DocumentationAuthorityAdapter(ArtifactRepository artifactRepository, ArtifactVersionRepository artifactVersionRepository) {
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
	}

	/**
	 * {@code websiteRequirementsRef} is schema-required, so - unlike {@code customerProfileRef} -
	 * its absence throws rather than returning an empty optional: by contract this method is only
	 * called once preflight has already confirmed a current canonical {@code website-requirements}
	 * artifact exists for the project, so reaching this branch means that invariant was violated,
	 * not a normal "optional authority is absent" case.
	 */
	public DocumentationAuthoritySnapshot buildAuthoritySnapshot(
			UUID projectId, WebsiteImplementationCandidate candidate, QaResult qaResult) {
		Optional<String> customerProfileRef = latestVersion(projectId, CUSTOMER_PROFILE_TYPE).map(v -> v.getId().toString());
		String websiteRequirementsRef = latestVersion(projectId, WEBSITE_REQUIREMENTS_TYPE)
				.map(v -> v.getId().toString())
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.CANONICAL_ARTIFACT_NOT_FOUND,
						"No canonical '" + WEBSITE_REQUIREMENTS_TYPE + "' artifact exists for project " + projectId));

		return new DocumentationAuthoritySnapshot(
				customerProfileRef,
				websiteRequirementsRef,
				candidate.getSourceDesignRef(),
				candidate.getId().toString(),
				qaResult.getId().toString(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
	}

	public DocumentationQaState buildQaState(WebsiteImplementationCandidate candidate, QaResult qaResult) {
		return new DocumentationQaState(
				qaResult.getId().toString(), candidate.getId().toString(), "FULL_RELEASE", qaResult.getGateOutcome());
	}

	private Optional<ArtifactVersion> latestVersion(UUID projectId, String type) {
		Optional<Artifact> artifact = artifactRepository.findByProjectIdAndType(projectId, type);
		if (artifact.isEmpty()) {
			return Optional.empty();
		}
		return artifactVersionRepository.findTopByArtifactIdOrderByVersionNumberDesc(artifact.get().getId());
	}
}
