package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Website Developer execution/accepted-Candidate status API (AIW-160) - exposes exactly
 * what {@link DeveloperExecutionStatusResponse} documents, and nothing else: no provider
 * credentials, no raw tool payloads, no execution transcripts, since none of those are ever read
 * from here in the first place (only {@link AgentExecution}'s own plain-string fields and {@code
 * WebsiteImplementationCandidate}'s own already-validated content are touched).
 *
 * <p>A caller-supplied {@code executionId} that exists but belongs to a different project, or to a
 * different agent entirely, is treated identically to one that does not exist at all - this
 * controller never reveals which case it was.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/developer-executions")
class DeveloperExecutionStatusController {

	static final String DEVELOPER_AGENT_ID = "developer-agent";

	private final ProjectRepository projectRepository;
	private final AgentExecutionRepository agentExecutionRepository;
	private final WebsiteImplementationCandidateRepository candidateRepository;

	DeveloperExecutionStatusController(
			ProjectRepository projectRepository,
			AgentExecutionRepository agentExecutionRepository,
			WebsiteImplementationCandidateRepository candidateRepository) {
		this.projectRepository = projectRepository;
		this.agentExecutionRepository = agentExecutionRepository;
		this.candidateRepository = candidateRepository;
	}

	@GetMapping("/{executionId}")
	ResponseEntity<DeveloperExecutionStatusResponse> get(@PathVariable UUID projectId, @PathVariable UUID executionId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
		}

		AgentExecution execution = agentExecutionRepository
				.findById(executionId)
				.filter(found -> found.getProjectId().equals(projectId))
				.filter(found -> DEVELOPER_AGENT_ID.equals(found.getAgentId()))
				.orElseThrow(() -> new ApplicationException(
						ErrorCode.DEVELOPER_EXECUTION_NOT_FOUND,
						"No Website Developer execution with id " + executionId + " for project " + projectId));

		return ResponseEntity.ok(DeveloperExecutionStatusResponse.from(
				execution, candidateRepository.findByAgentExecutionId(executionId).orElse(null)));
	}
}
