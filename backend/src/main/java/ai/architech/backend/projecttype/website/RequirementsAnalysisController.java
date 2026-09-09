package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.FileProjectInputRepository;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import ai.architech.backend.core.projectinput.StructuredProjectInputRepository;
import ai.architech.backend.core.runner.RetryBudgetExhaustedException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers one Requirements Analysis run for a Website Project (AIW-55) - the trigger endpoint
 * that was previously missing (see the product-usability note this platform's own memory
 * carried: the pipeline was fully wired end-to-end, but nothing external could start it).
 *
 * <p>Guards against "uncontrolled duplicate executions" (AIW-55 AC) with two checks: the
 * project must actually have some customer input to analyze, and no execution for this
 * project may already be RUNNING. The second check is a best-effort narrowing, not a hard
 * lock - two truly simultaneous requests could both pass it before either persists an
 * execution. Acceptable for V1: this is a single local-dev instance, and the mock AI Gateway
 * call is effectively instant, so the race window is negligible. A real lock would be
 * over-engineering for a risk this small; revisit if a real (slow) AI provider and concurrent
 * usage both become real.
 *
 * <p>A model/runtime failure (the retry budget exhausted without ever getting a candidate) is
 * translated to 502 with a static, generic message - distinct in both HTTP status and response
 * shape from a completed run that failed deterministic validation (201, {@link
 * RequirementsAnalysisResponse} with {@code succeeded: false}). See AIW-56.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/requirements-analysis")
class RequirementsAnalysisController {

	private final ProjectRepository projectRepository;
	private final ProjectInputRepository projectInputRepository;
	private final StructuredProjectInputRepository structuredProjectInputRepository;
	private final FileProjectInputRepository fileProjectInputRepository;
	private final AgentExecutionRepository agentExecutionRepository;
	private final RequirementsAnalysisRunner requirementsAnalysisRunner;

	RequirementsAnalysisController(
			ProjectRepository projectRepository,
			ProjectInputRepository projectInputRepository,
			StructuredProjectInputRepository structuredProjectInputRepository,
			FileProjectInputRepository fileProjectInputRepository,
			AgentExecutionRepository agentExecutionRepository,
			RequirementsAnalysisRunner requirementsAnalysisRunner) {
		this.projectRepository = projectRepository;
		this.projectInputRepository = projectInputRepository;
		this.structuredProjectInputRepository = structuredProjectInputRepository;
		this.fileProjectInputRepository = fileProjectInputRepository;
		this.agentExecutionRepository = agentExecutionRepository;
		this.requirementsAnalysisRunner = requirementsAnalysisRunner;
	}

	@PostMapping
	ResponseEntity<RequirementsAnalysisResponse> start(@PathVariable UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
		}
		if (!hasAnyInput(projectId)) {
			throw new ApplicationException(
					ErrorCode.PROJECT_HAS_NO_INPUT, "Project has no customer input to analyze yet");
		}
		if (agentExecutionRepository.existsByProjectIdAndStatus(projectId, AgentExecutionStatus.RUNNING)) {
			throw new ApplicationException(
					ErrorCode.REQUIREMENTS_ANALYSIS_ALREADY_RUNNING,
					"A requirements analysis is already running for this project");
		}

		RequirementsAnalysisResult result;
		try {
			result = requirementsAnalysisRunner.run(projectId);
		} catch (RetryBudgetExhaustedException e) {
			// Deliberately a hand-written, static message - never e.getMessage() or e.getCause()
			// - so nothing from the model/runtime layer (which, once a real AI provider exists,
			// could carry provider-internal detail) can ever reach the client (AIW-56 AC: no
			// secrets/internals leak; model/runtime failure stays distinguishable from a
			// validation failure by both HTTP status and response shape - see
			// RequirementsAnalysisResponse for the latter).
			throw new ApplicationException(
					ErrorCode.MODEL_RUNTIME_FAILURE,
					"Requirements analysis could not run: the model/runtime failed on every permitted attempt. No candidate output was produced.");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(RequirementsAnalysisResponse.from(result));
	}

	private boolean hasAnyInput(UUID projectId) {
		return projectInputRepository.existsByProjectId(projectId)
				|| structuredProjectInputRepository.existsByProjectId(projectId)
				|| fileProjectInputRepository.existsByProjectId(projectId);
	}
}
