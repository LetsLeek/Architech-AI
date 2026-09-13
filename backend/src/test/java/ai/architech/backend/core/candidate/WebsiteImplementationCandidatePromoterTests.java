package ai.architech.backend.core.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.sandbox.Workspace;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WebsiteImplementationCandidatePromoterTests {

	@Mock
	private WebsiteImplementationCandidateRepository repository;

	@Mock
	private HandoffFreezeGate handoffFreezeGate;

	private WebsiteImplementationCandidatePromoter promoter;

	private static final String DEVELOPER_RESULT =
			"""
			{"targetDesign": {"designArtifactVersionRef": "design-v1", "proposalLocalRef": "prop-a"},
			 "implementationSummary": "Implemented the home page.",
			 "implementationAnchors": [{"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]}],
			 "functionalBindings": [{"requirementRef": "req-func-1", "designLocalRefs": ["sec-a-hero"], "status": "IMPLEMENTED_LOCAL"}],
			 "unresolvedIssues": []}""";

	private static final String EXECUTION_INPUT =
			"""
			{"technicalContext": {"runtimeProfileRef": "website-react-typescript-vite-client-v1"}}""";

	@BeforeEach
	void setUp() {
		this.promoter = new WebsiteImplementationCandidatePromoter(repository, handoffFreezeGate);
	}

	@Test
	void successfullyPersistsAVerifiedCandidate() {
		UUID projectId = UUID.randomUUID();
		UUID agentExecutionId = UUID.randomUUID();
		Workspace workspace = new Workspace(Path.of(System.getProperty("java.io.tmpdir")));
		FrozenHandoffSnapshot snapshot = new FrozenHandoffSnapshot("snapshot-hash-1", Instant.now());
		when(repository.findByAgentExecutionId(agentExecutionId)).thenReturn(Optional.empty());
		when(handoffFreezeGate.matchesCurrentState(workspace, snapshot)).thenReturn(true);
		when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

		WebsiteImplementationCandidate candidate =
				promoter.promote(projectId, agentExecutionId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(candidate.getProjectId()).isEqualTo(projectId);
		assertThat(candidate.getAgentExecutionId()).isEqualTo(agentExecutionId);
		assertThat(candidate.getSourceDesignArtifactVersionRef()).isEqualTo("design-v1");
		assertThat(candidate.getSourceDesignProposalLocalRef()).isEqualTo("prop-a");
		assertThat(candidate.getRuntimeProfileRef()).isEqualTo("website-react-typescript-vite-client-v1");
		assertThat(candidate.getRepositoryStateRef()).isEqualTo("snapshot-hash-1");
		assertThat(candidate.getImplementationSummary()).isEqualTo("Implemented the home page.");
		assertThat(candidate.getImplementationAnchors()).contains("page-a-home");
		assertThat(candidate.getFunctionalBindings()).contains("req-func-1");
		assertThat(candidate.getUnresolvedIssues()).isEqualTo("[]");
	}

	@Test
	void refusesToPersistWhenTheWorkspaceStateNoLongerMatchesTheFrozenSnapshot() {
		UUID agentExecutionId = UUID.randomUUID();
		Workspace workspace = new Workspace(Path.of(System.getProperty("java.io.tmpdir")));
		FrozenHandoffSnapshot snapshot = new FrozenHandoffSnapshot("snapshot-hash-1", Instant.now());
		when(repository.findByAgentExecutionId(agentExecutionId)).thenReturn(Optional.empty());
		when(handoffFreezeGate.matchesCurrentState(workspace, snapshot)).thenReturn(false);

		assertThatThrownBy(() -> promoter.promote(
						UUID.randomUUID(), agentExecutionId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot))
				.isInstanceOf(RepositoryStateMismatchException.class);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void aRetryForAnAlreadyAcceptedExecutionReturnsTheExistingCandidateUnchanged() {
		UUID agentExecutionId = UUID.randomUUID();
		Workspace workspace = new Workspace(Path.of(System.getProperty("java.io.tmpdir")));
		FrozenHandoffSnapshot snapshot = new FrozenHandoffSnapshot("snapshot-hash-1", Instant.now());
		WebsiteImplementationCandidate existing = new WebsiteImplementationCandidate(
				UUID.randomUUID(), agentExecutionId, "design-v1", "prop-a", "runtime-v1", "snapshot-hash-1", "summary", "[]", "[]", "[]");
		when(repository.findByAgentExecutionId(agentExecutionId)).thenReturn(Optional.of(existing));

		WebsiteImplementationCandidate result = promoter.promote(
				UUID.randomUUID(), agentExecutionId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(result).isSameAs(existing);
		verify(handoffFreezeGate, never()).matchesCurrentState(any(), any());
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void anUnrelatedPersistenceFailurePropagatesUncaught() {
		UUID agentExecutionId = UUID.randomUUID();
		Workspace workspace = new Workspace(Path.of(System.getProperty("java.io.tmpdir")));
		FrozenHandoffSnapshot snapshot = new FrozenHandoffSnapshot("snapshot-hash-1", Instant.now());
		when(repository.findByAgentExecutionId(agentExecutionId)).thenReturn(Optional.empty());
		when(handoffFreezeGate.matchesCurrentState(workspace, snapshot)).thenReturn(true);
		when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("db unreachable"));

		assertThatThrownBy(() -> promoter.promote(
						UUID.randomUUID(), agentExecutionId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	@Test
	void fallsBackToTheExistingRowWhenAConcurrentRetryLosesTheUniqueConstraintRace() {
		UUID agentExecutionId = UUID.randomUUID();
		Workspace workspace = new Workspace(Path.of(System.getProperty("java.io.tmpdir")));
		FrozenHandoffSnapshot snapshot = new FrozenHandoffSnapshot("snapshot-hash-1", Instant.now());
		WebsiteImplementationCandidate concurrentlyInsertedByAnotherCaller = new WebsiteImplementationCandidate(
				UUID.randomUUID(), agentExecutionId, "design-v1", "prop-a", "runtime-v1", "snapshot-hash-1", "summary", "[]", "[]", "[]");
		when(repository.findByAgentExecutionId(agentExecutionId))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(concurrentlyInsertedByAnotherCaller));
		when(handoffFreezeGate.matchesCurrentState(workspace, snapshot)).thenReturn(true);
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

		WebsiteImplementationCandidate result = promoter.promote(
				UUID.randomUUID(), agentExecutionId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(result).isSameAs(concurrentlyInsertedByAnotherCaller);
	}
}
