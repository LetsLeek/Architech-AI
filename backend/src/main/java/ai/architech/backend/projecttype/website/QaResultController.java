package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.AuthorityIssueRepository;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.EvaluationIssueRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only Website QA result API (AIW-208) - exposes the most recent {@link QaResult} across all
 * of a project's {@code WebsiteImplementationCandidate}s, mirroring {@code
 * DeveloperExecutionStatusController}'s own read-only/project-scoped/no-raw-payload conventions.
 *
 * <p><b>Deliberately no trigger endpoint here</b> - starting a QA run from a bare {@code
 * projectId} needs a QA-specific execution-input assembler (the {@code qa-execution-input.v1}
 * equivalent of {@code DeveloperExecutionInputAssembler}, AIW-151) that does not exist anywhere in
 * this codebase yet (confirmed this session), unlike Developer generation's own trigger (AIW-212),
 * which could reuse an already-built assembler. Building that assembler for real, rather than a
 * half-real placeholder, is separate follow-up scope; this ticket's own real, tested scope is the
 * conversion/persistence layer {@link QaResultAssemblyService} provides and exposing its result
 * here once some other caller has already run {@code QaSemanticReviewOrchestrator} and assembled a
 * {@link QaResult} from it.
 *
 * <p>No project, or a project with no {@code WebsiteImplementationCandidate} yet, or one with
 * candidates but no {@link QaResult} recorded for any of them yet, are all reported identically as
 * {@link ErrorCode#QA_RESULT_NOT_FOUND} - this controller never reveals which case it was, the
 * same "exists but wrong scope reads as not found" idiom {@code DeveloperExecutionStatusController}
 * already establishes for a wrong-project execution id.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/qa-results")
class QaResultController {

	private final ProjectRepository projectRepository;
	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final QaResultRepository qaResultRepository;
	private final CandidateFindingRepository candidateFindingRepository;
	private final AuthorityIssueRepository authorityIssueRepository;
	private final EvaluationIssueRepository evaluationIssueRepository;
	private final ObjectMapper objectMapper;

	QaResultController(
			ProjectRepository projectRepository,
			WebsiteImplementationCandidateRepository candidateRepository,
			QaResultRepository qaResultRepository,
			CandidateFindingRepository candidateFindingRepository,
			AuthorityIssueRepository authorityIssueRepository,
			EvaluationIssueRepository evaluationIssueRepository,
			ObjectMapper objectMapper) {
		this.projectRepository = projectRepository;
		this.candidateRepository = candidateRepository;
		this.qaResultRepository = qaResultRepository;
		this.candidateFindingRepository = candidateFindingRepository;
		this.authorityIssueRepository = authorityIssueRepository;
		this.evaluationIssueRepository = evaluationIssueRepository;
		this.objectMapper = objectMapper;
	}

	@GetMapping
	ResponseEntity<QaResultResponse> latest(@PathVariable UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
		}

		List<UUID> candidateIds =
				candidateRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(WebsiteImplementationCandidate::getId).toList();

		List<QaResult> qaResults =
				candidateIds.isEmpty() ? List.of() : qaResultRepository.findByTestedCandidateIdInOrderByCreatedAtDesc(candidateIds);

		QaResult qaResult = qaResults.stream()
				.findFirst()
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.QA_RESULT_NOT_FOUND, "No QA result recorded yet for project " + projectId));

		int findingCount = candidateFindingRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId()).size();
		int authorityIssueCount = authorityIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId()).size();
		int evaluationIssueCount = evaluationIssueRepository.findByQaResultIdOrderByCreatedAtAsc(qaResult.getId()).size();

		return ResponseEntity.ok(
				QaResultResponse.from(qaResult, findingCount, authorityIssueCount, evaluationIssueCount, objectMapper));
	}
}
