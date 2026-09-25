package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.qa.AuthorityIssue;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.EvaluationIssue;
import ai.architech.backend.core.qa.invariants.FindingFingerprintNormalizer;
import ai.architech.backend.core.qa.policy.QaEvaluationState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Parses one already-validated {@code semantic-qa-review-output} candidate (AIW-173's own {@code
 * QaSemanticReviewOrchestrator} has already run output-contract, JSON Schema and identity
 * validation on it before this converter ever sees it) into real, unsaved {@link
 * CandidateFinding}/{@link AuthorityIssue}/{@link EvaluationIssue} rows - the conversion {@code
 * QaSemanticReviewOrchestrator}'s own javadoc names as "AIW-174's scope" and that, until this
 * ticket (AIW-208), had never actually been built anywhere in this codebase.
 *
 * <p><b>Fingerprint</b> is always the real {@link FindingFingerprintNormalizer} output over each
 * finding's own {@code testedCandidateId}/{@code findingCode}/{@code normativeBasis} refs/{@code
 * context} fields - never a random value, so two QA attempts that surface the same underlying
 * defect fingerprint identically (AIW-174's own "cross-attempt finding identity" requirement).
 *
 * <p><b>{@code requiredCoverageComplete}</b> is derived directly from the candidate's own {@code
 * semanticReviewCoverage} array: {@code true} only when it is non-empty and every entry reports
 * {@code COMPLETED}. This is the semantic reviewer's own self-reported completeness, not a real
 * cross-check against a {@code QaProfile}'s own declared required domains - {@code
 * QAPolicyAggregator}'s own javadoc already names that real cross-check ({@code
 * DomainResultInvariantValidator}/{@code RequirementCoverageValidator}) as separate, not-yet-
 * ticketed scope, and {@link #convert} accepts this honest proxy rather than inventing that
 * validator here.
 *
 * <p><b>{@code materiallyUnfulfilledMustRequirementExists}</b> is {@code true} exactly when at
 * least one parsed finding carries the {@code REQ_MUST_UNFULFILLED} code - {@code
 * QAPolicyAggregator}'s own javadoc names this as "the one code whose own purpose is exactly 'a
 * must Requirement was materially unfulfilled'", so detecting its presence in the real parsed
 * findings is the literal, non-invented reading of that signal, not a fabricated summary.
 *
 * <p><b>{@code evaluationState}</b> is {@link QaEvaluationState#COMPLETE} when {@code
 * requiredCoverageComplete} is {@code true}, otherwise {@link QaEvaluationState#PARTIAL} - {@link
 * QaEvaluationState#INVALID} is never produced here, since this converter only ever runs on a
 * candidate {@code QaSemanticReviewOrchestrator} has already accepted as structurally valid; a
 * genuinely invalid execution attempt never reaches this class at all.
 *
 * <p><b>{@code domainResultsJson}</b> covers exactly the domains referenced by the candidate's own
 * {@code semanticReviewCoverage} entries, finding {@code primaryDomain}s, and issue {@code
 * affectedDomains} - each reported {@code APPLICABLE} (something referenced it, so it was
 * evaluated), never {@code NOT_APPLICABLE}: a real applicability determination needs {@code
 * QaDomainApplicabilityResolver}'s own {@code ProductAuthorityContext} input, which does not exist
 * at this call site - a real, separate gap, not silently worked around.
 */
@Component
public class SemanticQaReviewOutputToFindingsConverter {

	private static final String REQ_MUST_UNFULFILLED_CODE = "REQ_MUST_UNFULFILLED";

	private final ObjectMapper objectMapper;
	private final FindingFingerprintNormalizer fingerprintNormalizer;

	SemanticQaReviewOutputToFindingsConverter(ObjectMapper objectMapper, FindingFingerprintNormalizer fingerprintNormalizer) {
		this.objectMapper = objectMapper;
		this.fingerprintNormalizer = fingerprintNormalizer;
	}

	public ConvertedQaCandidate convert(UUID qaResultId, UUID qaExecutionId, UUID testedCandidateId, String candidateJson) {
		JsonNode root = objectMapper.readTree(candidateJson);

		List<CandidateFinding> findings = new ArrayList<>();
		for (JsonNode findingNode : root.path("findingCandidates")) {
			findings.add(toFinding(qaResultId, qaExecutionId, testedCandidateId, findingNode));
		}

		List<AuthorityIssue> authorityIssues = new ArrayList<>();
		for (JsonNode issueNode : root.path("authorityIssueCandidates")) {
			authorityIssues.add(toAuthorityIssue(qaResultId, qaExecutionId, testedCandidateId, issueNode));
		}

		List<EvaluationIssue> evaluationIssues = new ArrayList<>();
		for (JsonNode issueNode : root.path("evaluationIssueCandidates")) {
			evaluationIssues.add(toEvaluationIssue(qaResultId, qaExecutionId, testedCandidateId, issueNode));
		}

		List<JsonNode> coverageEntries = new ArrayList<>();
		for (JsonNode entry : root.path("semanticReviewCoverage")) {
			coverageEntries.add(entry);
		}
		boolean requiredCoverageComplete = !coverageEntries.isEmpty()
				&& coverageEntries.stream().allMatch(entry -> "COMPLETED".equals(entry.path("status").asString(null)));

		boolean materiallyUnfulfilledMustRequirementExists =
				findings.stream().anyMatch(finding -> REQ_MUST_UNFULFILLED_CODE.equals(finding.getFindingCode()));

		QaEvaluationState evaluationState = requiredCoverageComplete ? QaEvaluationState.COMPLETE : QaEvaluationState.PARTIAL;

		String domainResultsJson =
				objectMapper.writeValueAsString(buildDomainResults(findings, authorityIssues, evaluationIssues, coverageEntries));

		return new ConvertedQaCandidate(
				findings,
				authorityIssues,
				evaluationIssues,
				domainResultsJson,
				requiredCoverageComplete,
				materiallyUnfulfilledMustRequirementExists,
				evaluationState);
	}

	private CandidateFinding toFinding(UUID qaResultId, UUID qaExecutionId, UUID testedCandidateId, JsonNode node) {
		String findingCode = node.path("findingCode").asString();
		String primaryDomain = node.path("primaryDomain").asString();
		String severity = node.path("proposedSeverity").asString();
		String summary = node.path("summary").asString();
		String diagnosticDetails = node.path("diagnosticDetails").asString(null);

		List<String> normativeBasisRefs = new ArrayList<>();
		for (JsonNode entry : node.path("normativeBasis")) {
			normativeBasisRefs.add(entry.path("ref").asString(null));
		}

		JsonNode context = node.has("context") ? node.get("context") : null;
		String route = context == null ? null : context.path("route").asString(null);
		String viewportRef = context == null ? null : context.path("viewportRef").asString(null);
		String locale = context == null ? null : context.path("locale").asString(null);
		String interactionState = context == null ? null : context.path("interactionState").asString(null);
		List<String> implementationAnchorRefs = new ArrayList<>();
		if (context != null && context.has("implementationAnchorRefs")) {
			for (JsonNode ref : context.get("implementationAnchorRefs")) {
				implementationAnchorRefs.add(ref.asString());
			}
		}

		String fingerprint = fingerprintNormalizer.fingerprint(
				testedCandidateId, findingCode, normativeBasisRefs, route, viewportRef, locale, interactionState, implementationAnchorRefs);

		return new CandidateFinding(
				qaResultId,
				qaExecutionId,
				testedCandidateId,
				findingCode,
				primaryDomain,
				severity,
				objectMapper.writeValueAsString(node.path("normativeBasis")),
				summary,
				diagnosticDetails,
				context == null ? null : objectMapper.writeValueAsString(context),
				objectMapper.writeValueAsString(node.path("evidenceRefs")),
				fingerprint,
				provenanceJson(node.path("localRef").asString(null)));
	}

	private AuthorityIssue toAuthorityIssue(UUID qaResultId, UUID qaExecutionId, UUID testedCandidateId, JsonNode node) {
		return new AuthorityIssue(
				qaResultId,
				qaExecutionId,
				testedCandidateId,
				node.path("code").asString(),
				node.path("summary").asString(),
				objectMapper.writeValueAsString(node.path("affectedAuthorityRefs")),
				objectMapper.writeValueAsString(node.path("expectedAuthorityTypes")),
				objectMapper.writeValueAsString(node.path("affectedDomains")),
				objectMapper.writeValueAsString(node.path("evidenceRefs")),
				provenanceJson(node.path("localRef").asString(null)));
	}

	private EvaluationIssue toEvaluationIssue(UUID qaResultId, UUID qaExecutionId, UUID testedCandidateId, JsonNode node) {
		return new EvaluationIssue(
				qaResultId,
				qaExecutionId,
				testedCandidateId,
				node.path("code").asString(),
				node.path("summary").asString(),
				objectMapper.writeValueAsString(node.path("affectedDomains")),
				node.path("requiredCheckRef").asString(null),
				node.path("toolCapabilityRef").asString(null),
				node.path("executionSurfaceRef").asString(null),
				objectMapper.writeValueAsString(node.path("evidenceRefs")),
				provenanceJson(node.path("localRef").asString(null)));
	}

	private String provenanceJson(String localRef) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("detectionMethod", "SEMANTIC");
		if (localRef != null) {
			node.put("candidateLocalRef", localRef);
		}
		return objectMapper.writeValueAsString(node);
	}

	private ArrayNode buildDomainResults(
			List<CandidateFinding> findings,
			List<AuthorityIssue> authorityIssues,
			List<EvaluationIssue> evaluationIssues,
			List<JsonNode> coverageEntries) {
		Set<String> domains = new LinkedHashSet<>();
		Map<String, List<String>> findingRefsByDomain = new LinkedHashMap<>();
		Map<String, List<String>> authorityIssueRefsByDomain = new LinkedHashMap<>();
		Map<String, List<String>> evaluationIssueRefsByDomain = new LinkedHashMap<>();
		Map<String, LinkedHashSet<String>> semanticReviewRefsByDomain = new LinkedHashMap<>();
		Map<String, String> coverageStatusByDomain = new LinkedHashMap<>();

		for (JsonNode entry : coverageEntries) {
			String domain = entry.path("domain").asString(null);
			if (domain == null) {
				continue;
			}
			domains.add(domain);
			coverageStatusByDomain.put(domain, entry.path("status").asString(null));
			semanticReviewRefsByDomain.computeIfAbsent(domain, d -> new LinkedHashSet<>()).add(entry.path("reviewTaskRef").asString());
		}
		for (CandidateFinding finding : findings) {
			String domain = finding.getPrimaryDomain();
			domains.add(domain);
			findingRefsByDomain.computeIfAbsent(domain, d -> new ArrayList<>()).add(finding.getId().toString());
		}
		for (AuthorityIssue issue : authorityIssues) {
			for (String domain : jsonArrayStrings(issue.getAffectedDomainsJson())) {
				domains.add(domain);
				authorityIssueRefsByDomain.computeIfAbsent(domain, d -> new ArrayList<>()).add(issue.getId().toString());
			}
		}
		for (EvaluationIssue issue : evaluationIssues) {
			for (String domain : jsonArrayStrings(issue.getAffectedDomainsJson())) {
				domains.add(domain);
				evaluationIssueRefsByDomain.computeIfAbsent(domain, d -> new ArrayList<>()).add(issue.getId().toString());
			}
		}

		ArrayNode result = objectMapper.createArrayNode();
		for (String domain : domains) {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("domain", domain);
			node.put("applicability", "APPLICABLE");
			String coverageStatus = coverageStatusByDomain.get(domain);
			if (coverageStatus != null) {
				node.put("coverage", mapCoverageStatus(coverageStatus));
			}
			node.set("findingRefs", stringArray(findingRefsByDomain.getOrDefault(domain, List.of())));
			node.set("authorityIssueRefs", stringArray(authorityIssueRefsByDomain.getOrDefault(domain, List.of())));
			node.set("evaluationIssueRefs", stringArray(evaluationIssueRefsByDomain.getOrDefault(domain, List.of())));
			node.set("executedCheckRefs", objectMapper.createArrayNode());
			node.set(
					"semanticReviewRefs",
					stringArray(List.copyOf(semanticReviewRefsByDomain.getOrDefault(domain, new LinkedHashSet<>()))));
			result.add(node);
		}
		return result;
	}

	private String mapCoverageStatus(String semanticReviewCoverageStatus) {
		return switch (semanticReviewCoverageStatus) {
			case "COMPLETED" -> "COMPLETE";
			case "PARTIAL" -> "PARTIAL";
			default -> "NONE";
		};
	}

	private ArrayNode stringArray(List<String> values) {
		ArrayNode array = objectMapper.createArrayNode();
		values.forEach(array::add);
		return array;
	}

	private List<String> jsonArrayStrings(String json) {
		List<String> values = new ArrayList<>();
		for (JsonNode item : objectMapper.readTree(json)) {
			values.add(item.asString());
		}
		return values;
	}
}
