package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.qa.QaResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What {@link QaResultController} returns for one project's latest {@link QaResult} (AIW-208) -
 * gate outcome, hold reasons, and per-kind counts, never a raw {@code domainResultsJson}/{@code
 * provenanceJson} blob (same "no raw tool payloads" convention {@code
 * DeveloperExecutionStatusController}'s own javadoc already establishes).
 */
public record QaResultResponse(
		UUID qaResultId,
		UUID qaExecutionId,
		UUID testedCandidateId,
		String qaProfileRef,
		String evaluationState,
		String gateOutcome,
		List<String> holdReasons,
		int findingCount,
		int authorityIssueCount,
		int evaluationIssueCount,
		Instant createdAt) {

	static QaResultResponse from(
			QaResult qaResult, int findingCount, int authorityIssueCount, int evaluationIssueCount, ObjectMapper objectMapper) {
		return new QaResultResponse(
				qaResult.getId(),
				qaResult.getQaExecutionId(),
				qaResult.getTestedCandidateId(),
				qaResult.getQaProfileRef(),
				qaResult.getEvaluationState(),
				qaResult.getGateOutcome(),
				stringList(qaResult.getHoldReasonsJson(), objectMapper),
				findingCount,
				authorityIssueCount,
				evaluationIssueCount,
				qaResult.getCreatedAt());
	}

	private static List<String> stringList(String json, ObjectMapper objectMapper) {
		List<String> values = new ArrayList<>();
		for (JsonNode item : objectMapper.readTree(json)) {
			values.add(item.asString());
		}
		return values;
	}
}
