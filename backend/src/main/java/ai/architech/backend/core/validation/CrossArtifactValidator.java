package ai.architech.backend.core.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Layer-6 (required deterministic cross-artifact) validation, per
 * {@code docs/core/RUNNER_VALIDATION_CONTRACT.md}: "machine-checkable consistency between the
 * required candidate artifacts".
 *
 * <p>Neither frozen schema defines a field that references the other artifact (no
 * customer-profile-ref in Website Requirements, no requirement-ref in Customer Profile), so
 * there is no candidate-to-candidate identity to cross-check. The one genuinely
 * machine-checkable, non-semantic invariant that spans both artifacts is reference integrity
 * against the evidence they share: both candidates were produced from the very same evidence
 * snapshot, so every {@code sourceRef} either one cites - wherever in its structure - must
 * resolve there. Checking this only per-artifact would miss nothing a combined check catches;
 * running it here, as one call producing one result, is what makes the two candidates "one
 * validation unit" per the ticket's acceptance criteria - if either artifact cites a foreign
 * ref, the whole pair fails together and neither becomes canonical.
 *
 * <p>Purely semantic cross-artifact questions (e.g. a requirement unnecessarily duplicating a
 * profile fact) are explicitly out of scope for deterministic validation per
 * {@code docs/core/REQUIREMENTS_VALIDATOR_CHECKLIST.md}'s Cross-Artifact section. Detects and
 * reports only: never repairs or mutates either candidate.
 */
@Component
public class CrossArtifactValidator {

	private final SourceRefValidator sourceRefValidator;
	private final ObjectMapper objectMapper;

	CrossArtifactValidator(SourceRefValidator sourceRefValidator, ObjectMapper objectMapper) {
		this.sourceRefValidator = sourceRefValidator;
		this.objectMapper = objectMapper;
	}

	public CrossArtifactValidationResult validate(
			UUID evidenceSnapshotId, String customerProfileJson, String websiteRequirementsJson) {
		List<CrossArtifactValidationIssue> issues = new ArrayList<>();
		issues.addAll(validateArtifact("customer-profile", customerProfileJson, evidenceSnapshotId));
		issues.addAll(validateArtifact("website-requirements", websiteRequirementsJson, evidenceSnapshotId));

		return issues.isEmpty() ? CrossArtifactValidationResult.passed() : new CrossArtifactValidationResult(false, issues);
	}

	private List<CrossArtifactValidationIssue> validateArtifact(String artifactType, String candidateJson, UUID evidenceSnapshotId) {
		JsonNode root;
		try {
			root = objectMapper.readTree(candidateJson);
		} catch (RuntimeException e) {
			return List.of(new CrossArtifactValidationIssue(artifactType, "$", "candidate is not valid JSON: " + e.getMessage()));
		}

		Set<String> citedRefs = SourceRefExtractor.extract(root);
		SourceRefValidationResult result = sourceRefValidator.validate(evidenceSnapshotId, citedRefs);

		return result.issues().stream()
				.map(issue -> new CrossArtifactValidationIssue(artifactType, issue.ref(), issue.reason()))
				.toList();
	}
}
