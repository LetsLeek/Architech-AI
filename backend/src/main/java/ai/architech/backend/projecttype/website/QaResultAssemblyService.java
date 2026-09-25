package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.qa.AuthorityIssueRepository;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.EvaluationIssueRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.PolicyEvaluationRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.qa.policy.QAPolicyAggregator;
import ai.architech.backend.core.qa.policy.QaPolicyAggregationResult;
import ai.architech.backend.core.qa.profiles.QaProfile;
import ai.architech.backend.core.qa.profiles.QaProfileLoader;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Converts one already-validated {@code semantic-qa-review-output} candidate into the real,
 * authoritative {@link QaResult} row and its persisted children (AIW-208) - the missing "convert
 * the candidate into real, persisted rows" step {@code QaSemanticReviewOrchestrator}'s own
 * javadoc names as AIW-174's scope, and which, until this ticket, had never actually been built
 * anywhere in this codebase (confirmed this session: {@code new QaResult(} does not occur
 * anywhere in {@code backend/src/main/java} before this class).
 *
 * <p><b>Deliberately not called from {@code QaSemanticReviewOrchestrator} itself</b> - the same
 * "compose lower-level building blocks from a higher-level caller rather than modify an
 * already-tested class" reasoning {@code WebsiteGenerationDrivingService}'s own javadoc already
 * documents for AIW-212. {@link QaResultController} is that higher-level caller: it invokes this
 * service only after a {@code QaSemanticReviewResult} has already succeeded (output-contract,
 * schema and identity validation all passed), which is exactly this method's own precondition.
 *
 * <p><b>Does not build or wire {@code QaCheckRunner}/any deterministic check invocation</b> - a
 * real, separate, not-yet-wired gap (this ticket's own explicit scope boundary), not a silent
 * omission. Every {@code executedCheckRefs} entry {@link
 * SemanticQaReviewOutputToFindingsConverter} produces is therefore always empty. Likewise never
 * parses {@code remediationAssessmentCandidates} - {@code QaResult.remediationAssessmentRefsJson}
 * is always {@code "[]"} here; that conversion is real, separate, not-yet-ticketed scope.
 *
 * <p><b>Reuses an existing {@link EvidenceManifest} for this {@code QaExecution} if one already
 * exists, otherwise creates one</b> - {@code QaSemanticReviewOrchestrator.run} never creates one
 * itself (confirmed by reading it in full this session), so this is the first real writer of that
 * table for a full end-to-end run.
 */
@Component
public class QaResultAssemblyService {

	private final QaProfileLoader qaProfileLoader;
	private final QAPolicyAggregator policyAggregator;
	private final SemanticQaReviewOutputToFindingsConverter converter;
	private final QaResultRepository qaResultRepository;
	private final CandidateFindingRepository candidateFindingRepository;
	private final AuthorityIssueRepository authorityIssueRepository;
	private final EvaluationIssueRepository evaluationIssueRepository;
	private final PolicyEvaluationRepository policyEvaluationRepository;
	private final EvidenceManifestRepository evidenceManifestRepository;
	private final QaInputSnapshotRepository qaInputSnapshotRepository;
	private final ObjectMapper objectMapper;

	QaResultAssemblyService(
			QaProfileLoader qaProfileLoader,
			QAPolicyAggregator policyAggregator,
			SemanticQaReviewOutputToFindingsConverter converter,
			QaResultRepository qaResultRepository,
			CandidateFindingRepository candidateFindingRepository,
			AuthorityIssueRepository authorityIssueRepository,
			EvaluationIssueRepository evaluationIssueRepository,
			PolicyEvaluationRepository policyEvaluationRepository,
			EvidenceManifestRepository evidenceManifestRepository,
			QaInputSnapshotRepository qaInputSnapshotRepository,
			ObjectMapper objectMapper) {
		this.qaProfileLoader = qaProfileLoader;
		this.policyAggregator = policyAggregator;
		this.converter = converter;
		this.qaResultRepository = qaResultRepository;
		this.candidateFindingRepository = candidateFindingRepository;
		this.authorityIssueRepository = authorityIssueRepository;
		this.evaluationIssueRepository = evaluationIssueRepository;
		this.policyEvaluationRepository = policyEvaluationRepository;
		this.evidenceManifestRepository = evidenceManifestRepository;
		this.qaInputSnapshotRepository = qaInputSnapshotRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * {@code qaExecution} must already have exactly one {@link QaInputSnapshot} row (always true
	 * once {@code QaSemanticReviewOrchestrator.run} has created it) and {@code candidateJson} must
	 * already be the exact, schema/identity-validated {@code semantic-qa-review-output} content
	 * (this method re-derives nothing about validity, it only converts and persists).
	 */
	public QaResult assemble(QaExecution qaExecution, String candidateJson) {
		UUID qaResultId = UUID.randomUUID();
		QaProfile profile = qaProfileLoader.resolve(qaExecution.getQaProfileRef());

		ConvertedQaCandidate converted =
				converter.convert(qaResultId, qaExecution.getId(), qaExecution.getTestedCandidateId(), candidateJson);

		QaPolicyAggregationResult aggregation = policyAggregator.aggregate(
				qaResultId,
				qaExecution.getId(),
				qaExecution.getTestedCandidateId(),
				profile,
				converted.evaluationState(),
				converted.findings(),
				converted.authorityIssues(),
				converted.evaluationIssues(),
				converted.requiredCoverageComplete(),
				converted.materiallyUnfulfilledMustRequirementExists());

		QaInputSnapshot inputSnapshot = latestInputSnapshot(qaExecution.getId());
		EvidenceManifest evidenceManifest = evidenceManifestRepository
				.findByQaExecutionId(qaExecution.getId())
				.orElseGet(() -> evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId())));

		QaResult qaResult = new QaResult(
				qaResultId,
				qaExecution.getId(),
				qaExecution.getTestedCandidateId(),
				profile.ref(),
				inputSnapshot.getId(),
				converted.evaluationState().name(),
				converted.domainResultsJson(),
				refsJson(converted.findings().stream().map(f -> f.getId().toString()).toList()),
				refsJson(converted.authorityIssues().stream().map(i -> i.getId().toString()).toList()),
				refsJson(converted.evaluationIssues().stream().map(i -> i.getId().toString()).toList()),
				refsJson(aggregation.policyEvaluations().stream().map(pe -> pe.getId().toString()).toList()),
				refsJson(List.of()),
				aggregation.gateOutcome().name(),
				refsJson(aggregation.holdReasons().stream().map(Enum::name).toList()),
				evidenceManifest.getId(),
				"{\"qaSystemVersion\":\"1.0.0\"}");

		QaResult saved = qaResultRepository.saveAndFlush(qaResult);

		converted.findings().forEach(candidateFindingRepository::saveAndFlush);
		converted.authorityIssues().forEach(authorityIssueRepository::saveAndFlush);
		converted.evaluationIssues().forEach(evaluationIssueRepository::saveAndFlush);
		aggregation.policyEvaluations().forEach(policyEvaluationRepository::saveAndFlush);

		return saved;
	}

	private QaInputSnapshot latestInputSnapshot(UUID qaExecutionId) {
		List<QaInputSnapshot> snapshots = qaInputSnapshotRepository.findByQaExecutionIdOrderByCreatedAtAsc(qaExecutionId);
		if (snapshots.isEmpty()) {
			throw new IllegalStateException("QaExecution " + qaExecutionId + " has no QaInputSnapshot yet");
		}
		return snapshots.get(snapshots.size() - 1);
	}

	private String refsJson(List<String> refs) {
		ArrayNode array = objectMapper.createArrayNode();
		refs.forEach(array::add);
		return objectMapper.writeValueAsString(array);
	}
}
