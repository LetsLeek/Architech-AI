package ai.architech.backend.core.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class WebsiteImplementationCandidateAcceptanceCoordinatorTests {

	@Mock
	private WebsiteImplementationCandidatePromoter promoter;

	@Mock
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Mock
	private AgentExecutionRepository agentExecutionRepository;

	private WebsiteImplementationCandidateAcceptanceCoordinator coordinator;

	private static final String DEVELOPER_RESULT = "{}";
	private static final String EXECUTION_INPUT = "{}";

	@BeforeEach
	void setUp() {
		coordinator = new WebsiteImplementationCandidateAcceptanceCoordinator(
				promoter, candidateRepository, agentExecutionRepository);
	}

	private Workspace workspace() {
		return new Workspace(Path.of(System.getProperty("java.io.tmpdir")));
	}

	private FrozenHandoffSnapshot snapshot() {
		return new FrozenHandoffSnapshot("snapshot-hash-1", Instant.now());
	}

	private WebsiteImplementationCandidate candidate(UUID projectId, UUID agentExecutionId) {
		return new WebsiteImplementationCandidate(
				projectId, agentExecutionId, "design-v1", "prop-a", "runtime-v1", "snapshot-hash-1", "summary", "[]", "[]", "[]");
	}

	@Test
	void promotesTheCandidateAndSucceedsTheExecutionOnAFreshAcceptance() {
		UUID projectId = UUID.randomUUID();
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		Workspace workspace = workspace();
		FrozenHandoffSnapshot snapshot = snapshot();
		WebsiteImplementationCandidate expected = candidate(projectId, execution.getId());
		when(promoter.promote(projectId, execution.getId(), DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot))
				.thenReturn(expected);

		WebsiteImplementationCandidate result =
				coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(result).isSameAs(expected);
		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		verify(agentExecutionRepository).saveAndFlush(execution);
	}

	@Test
	void aRetryAgainstAnAlreadySucceededExecutionReturnsTheExistingCandidateAsANoOp() {
		UUID projectId = UUID.randomUUID();
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		execution.succeed();
		WebsiteImplementationCandidate existing = candidate(projectId, execution.getId());
		when(candidateRepository.findByAgentExecutionId(execution.getId())).thenReturn(Optional.of(existing));

		WebsiteImplementationCandidate result =
				coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace(), snapshot());

		assertThat(result).isSameAs(existing);
		verifyNoInteractions(promoter);
		verify(agentExecutionRepository, never()).saveAndFlush(any());
	}

	@Test
	void aSucceededExecutionWithNoAcceptedCandidateIsARejectedInvariantViolation() {
		UUID projectId = UUID.randomUUID();
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		execution.succeed();
		when(candidateRepository.findByAgentExecutionId(execution.getId())).thenReturn(Optional.empty());

		assertThatThrownBy(() ->
						coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace(), snapshot()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("acceptance evidence is incomplete");
	}

	@Test
	void aRepositoryStoreFailureWhileReadingWorkspaceStateTerminatesTheExecutionAsErrorNotFailed() {
		UUID projectId = UUID.randomUUID();
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		UncheckedIOException repositoryStoreFailure = new UncheckedIOException("git ls-files failed", new IOException("boom"));
		when(promoter.promote(any(), any(), any(), any(), any(), any())).thenThrow(repositoryStoreFailure);

		assertThatThrownBy(() ->
						coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace(), snapshot()))
				.isSameAs(repositoryStoreFailure);

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.ERROR);
		verify(agentExecutionRepository).saveAndFlush(execution);
	}

	@Test
	void aCandidateDbPersistenceFailureTerminatesTheExecutionAsErrorNotFailed() {
		UUID projectId = UUID.randomUUID();
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		DataAccessResourceFailureException dbFailure = new DataAccessResourceFailureException("db unreachable");
		when(promoter.promote(any(), any(), any(), any(), any(), any())).thenThrow(dbFailure);

		assertThatThrownBy(() ->
						coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace(), snapshot()))
				.isSameAs(dbFailure);

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.ERROR);
		verify(agentExecutionRepository).saveAndFlush(execution);
	}

	@Test
	void aRepositoryStateMismatchTerminatesTheExecutionAsErrorNotFailed() {
		UUID projectId = UUID.randomUUID();
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		RepositoryStateMismatchException mismatch = new RepositoryStateMismatchException(execution.getId());
		when(promoter.promote(any(), any(), any(), any(), any(), any())).thenThrow(mismatch);

		assertThatThrownBy(() ->
						coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace(), snapshot()))
				.isSameAs(mismatch);

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.ERROR);
	}
}
