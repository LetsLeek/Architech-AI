package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.FileProjectInputRepository;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import ai.architech.backend.core.projectinput.StructuredProjectInputRepository;
import ai.architech.backend.core.runner.RetryBudgetExhaustedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plain-Mockito, no-Spring-context unit test: a {@link RetryBudgetExhaustedException} (model/
 * runtime failure - the retry budget exhausted without ever getting a candidate) can't
 * actually be forced through the real endpoint in the current test environment, since the
 * only registered AI provider is the always-succeeding mock - AgentRunner never throws under
 * it. Isolating just the controller with a stubbed {@link RequirementsAnalysisRunner} is what
 * makes this path testable at all, and keeps the assertion tight: not just "502", but that the
 * underlying cause's message never reaches the response (AIW-56 AC: no
 * secrets/internals leak).
 */
class RequirementsAnalysisControllerRuntimeFailureTests {

	@Test
	void translatesARetryBudgetExhaustedExceptionIntoASafeBadGatewayResponse() {
		UUID projectId = UUID.randomUUID();

		ProjectRepository projectRepository = mock(ProjectRepository.class);
		ProjectInputRepository projectInputRepository = mock(ProjectInputRepository.class);
		StructuredProjectInputRepository structuredProjectInputRepository = mock(StructuredProjectInputRepository.class);
		FileProjectInputRepository fileProjectInputRepository = mock(FileProjectInputRepository.class);
		AgentExecutionRepository agentExecutionRepository = mock(AgentExecutionRepository.class);
		RequirementsAnalysisRunner requirementsAnalysisRunner = mock(RequirementsAnalysisRunner.class);

		when(projectRepository.existsById(projectId)).thenReturn(true);
		when(projectInputRepository.existsByProjectId(projectId)).thenReturn(true);
		when(requirementsAnalysisRunner.run(projectId))
				.thenThrow(new RetryBudgetExhaustedException(
						"requirements-agent", 3, new RuntimeException("sk-live-totally-a-secret-provider-key")));

		RequirementsAnalysisController controller = new RequirementsAnalysisController(
				projectRepository,
				projectInputRepository,
				structuredProjectInputRepository,
				fileProjectInputRepository,
				agentExecutionRepository,
				requirementsAnalysisRunner);

		assertThatThrownBy(() -> controller.start(projectId))
				.isInstanceOf(ApplicationException.class)
				.satisfies(thrown -> {
					ApplicationException applicationException = (ApplicationException) thrown;
					assertThat(applicationException.errorCode()).isEqualTo(ErrorCode.MODEL_RUNTIME_FAILURE);
					assertThat(applicationException.getMessage()).doesNotContain("sk-live-totally-a-secret-provider-key");
				});
	}
}
