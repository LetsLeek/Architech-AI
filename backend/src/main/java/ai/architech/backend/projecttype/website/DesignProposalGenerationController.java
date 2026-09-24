package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.runner.RetryBudgetExhaustedException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers one Designer Agent run for a Website Project (AIW-124) - same shape/guard reasoning
 * as {@code RequirementsAnalysisController}. {@link DesignerAgentRunner#run} itself throws
 * {@link ErrorCode#CANONICAL_ARTIFACT_NOT_FOUND} if the project has no canonical
 * customer-profile/website-requirements yet (i.e. Requirements Analysis hasn't succeeded for
 * this project) - a real precondition the Requirements Agent itself never had, so this
 * controller doesn't duplicate that check itself.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/design-proposals")
class DesignProposalGenerationController {

	private final ProjectRepository projectRepository;
	private final AgentExecutionRepository agentExecutionRepository;
	private final DesignerAgentRunner designerAgentRunner;

	DesignProposalGenerationController(
			ProjectRepository projectRepository,
			AgentExecutionRepository agentExecutionRepository,
			DesignerAgentRunner designerAgentRunner) {
		this.projectRepository = projectRepository;
		this.agentExecutionRepository = agentExecutionRepository;
		this.designerAgentRunner = designerAgentRunner;
	}

	@PostMapping
	ResponseEntity<DesignProposalGenerationResponse> start(@PathVariable UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id " + projectId);
		}
		if (agentExecutionRepository.existsByProjectIdAndStatus(projectId, AgentExecutionStatus.RUNNING)) {
			throw new ApplicationException(
					ErrorCode.DESIGN_PROPOSAL_GENERATION_ALREADY_RUNNING,
					"A design proposal generation is already running for this project");
		}

		DesignProposalGenerationResult result;
		try {
			result = designerAgentRunner.run(projectId);
		} catch (RetryBudgetExhaustedException e) {
			// Deliberately a hand-written, static message - never e.getMessage()/e.getCause() -
			// same reasoning as RequirementsAnalysisController.
			throw new ApplicationException(
					ErrorCode.MODEL_RUNTIME_FAILURE,
					"Design proposal generation could not run: the model/runtime failed on every permitted attempt. No candidate output was produced.");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(DesignProposalGenerationResponse.from(result));
	}
}
