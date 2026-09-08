package ai.architech.backend.core.artifact;

public record RequirementsPersistenceResult(
		boolean persisted, ArtifactVersion customerProfileVersion, ArtifactVersion websiteRequirementsVersion) {

	public static RequirementsPersistenceResult rejected() {
		return new RequirementsPersistenceResult(false, null, null);
	}

	public static RequirementsPersistenceResult persisted(ArtifactVersion customerProfile, ArtifactVersion websiteRequirements) {
		return new RequirementsPersistenceResult(true, customerProfile, websiteRequirements);
	}
}
