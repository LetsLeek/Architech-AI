package ai.architech.backend.projecttype.website;

import java.util.List;
import java.util.UUID;

/** What the frontend gets back from starting a website generation run - one entry per A/B/C sibling. */
public record WebsiteGenerationResponse(boolean allSucceeded, List<SiblingResult> siblings) {

	public record SiblingResult(
			String proposalLocalRef, UUID executionId, String status, UUID candidateId, String infrastructureFailureMessage) {}

	static WebsiteGenerationResponse from(WebsiteGenerationOutcome outcome) {
		List<SiblingResult> siblings = outcome.siblings().stream()
				.map(sibling -> new SiblingResult(
						sibling.proposalLocalRef(),
						sibling.execution() != null ? sibling.execution().getId() : null,
						sibling.execution() != null ? sibling.execution().getStatus().name() : "INFRASTRUCTURE_FAILURE",
						sibling.candidate() != null ? sibling.candidate().getId() : null,
						sibling.infrastructureFailureMessage()))
				.toList();
		return new WebsiteGenerationResponse(outcome.allSucceeded(), siblings);
	}
}
