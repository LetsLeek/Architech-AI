package ai.architech.backend.projecttype.website;

import java.util.List;
import java.util.UUID;

/**
 * What the frontend gets back from starting a Requirements Analysis run. The run is currently
 * synchronous (the mock AI Gateway call is effectively instant, and no real provider exists
 * yet to make it worth building an async job model for), so by the time this is constructed
 * {@code status} is always a terminal one (SUCCEEDED or FAILED) - never PENDING/RUNNING.
 * {@code validationIssues} deliberately carries only what {@link RequirementsAnalysisRunner}
 * itself produces (deterministic validator messages) - never a raw exception message or
 * anything from the AI provider, so no provider credentials/internals can leak through here.
 */
public record RequirementsAnalysisResponse(UUID executionId, String status, boolean succeeded, List<String> validationIssues) {

	static RequirementsAnalysisResponse from(RequirementsAnalysisResult result) {
		return new RequirementsAnalysisResponse(
				result.execution().getId(),
				result.execution().getStatus().name(),
				result.succeeded(),
				result.validationIssues());
	}
}
