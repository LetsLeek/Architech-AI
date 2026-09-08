package ai.architech.backend.core.artifact;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * customer-profile and website-requirements are one atomic persistence unit for
 * Requirements Agent V1 (per ARTIFACT_PERSISTENCE_CONTRACT.md): either both become
 * canonical or neither does. {@code @Transactional} makes a mid-write failure roll back
 * both promotions - the "no half-persisted state observable" guarantee is Spring's
 * transaction manager doing its job here, not something this class re-implements.
 *
 * <p>Deliberately still hardcoded to exactly these two named artifact types (per the
 * frozen V1 contract) rather than a generic N-artifact atomic unit - there is no third
 * artifact to generalize for yet, and inventing that abstraction now would be guessing at
 * a shape nothing has asked for.
 */
@Component
public class RequirementsOutputPersister {

	public static final String CUSTOMER_PROFILE_TYPE = "customer-profile";
	public static final String WEBSITE_REQUIREMENTS_TYPE = "website-requirements";

	private final CandidatePromoter candidatePromoter;

	RequirementsOutputPersister(CandidatePromoter candidatePromoter) {
		this.candidatePromoter = candidatePromoter;
	}

	@Transactional
	public RequirementsPersistenceResult persist(
			UUID projectId,
			CandidateOutput customerProfile,
			boolean customerProfileValid,
			CandidateOutput websiteRequirements,
			boolean websiteRequirementsValid) {
		requireType(customerProfile, CUSTOMER_PROFILE_TYPE);
		requireType(websiteRequirements, WEBSITE_REQUIREMENTS_TYPE);

		if (!customerProfileValid || !websiteRequirementsValid) {
			return RequirementsPersistenceResult.rejected();
		}

		ArtifactVersion customerProfileVersion = candidatePromoter.promote(projectId, customerProfile);
		ArtifactVersion websiteRequirementsVersion = candidatePromoter.promote(projectId, websiteRequirements);

		return RequirementsPersistenceResult.persisted(customerProfileVersion, websiteRequirementsVersion);
	}

	private static void requireType(CandidateOutput candidate, String expectedType) {
		if (!expectedType.equals(candidate.getArtifactType())) {
			throw new IllegalArgumentException(
					"Expected candidate of type '" + expectedType + "' but was '" + candidate.getArtifactType() + "'");
		}
	}
}
