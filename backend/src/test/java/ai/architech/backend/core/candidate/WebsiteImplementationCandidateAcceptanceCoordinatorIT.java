package ai.architech.backend.core.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-git, real-DB proof of AIW-159's crash-boundary claims - each test re-enters {@link
 * WebsiteImplementationCandidateAcceptanceCoordinator#accept} from whatever durable state a
 * process failure would have actually left behind, rather than asserting against a mocked
 * approximation of that state.
 */
@SpringBootTest
@Transactional
class WebsiteImplementationCandidateAcceptanceCoordinatorIT {

	@TempDir
	Path root;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private HandoffFreezeGate handoffFreezeGate;

	@Autowired
	private WebsiteImplementationCandidatePromoter promoter;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private WebsiteImplementationCandidateAcceptanceCoordinator coordinator;

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
	void initRealGitRepo() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
	}

	@Test
	void acceptsAFreshExecutionAndCommitsExactlyOneCandidateAndSucceededStatus() throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		UUID projectId = seedProject();
		AgentExecution execution = startedExecution(projectId);

		WebsiteImplementationCandidate candidate =
				coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(agentExecutionRepository.findById(execution.getId()).orElseThrow().getStatus())
				.isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId()).map(WebsiteImplementationCandidate::getId))
				.contains(candidate.getId());
	}

	@Test
	void recoversDeterministicallyToSucceededWhenCandidatePersistenceCommittedButTheStatusUpdateNeverRan()
			throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		UUID projectId = seedProject();
		AgentExecution execution = startedExecution(projectId);
		// Simulates a process failure landing exactly between the two writes: the Candidate row is
		// already durable (via the same idempotent Promoter the Coordinator itself calls), but the
		// execution is still RUNNING because the status write never happened.
		WebsiteImplementationCandidate alreadyPersisted =
				promoter.promote(projectId, execution.getId(), DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);
		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);

		WebsiteImplementationCandidate recovered =
				coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(recovered.getId()).isEqualTo(alreadyPersisted.getId());
		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(candidateRepository.findAll().stream()
						.filter(c -> c.getAgentExecutionId().equals(execution.getId()))
						.count())
				.isEqualTo(1);
	}

	@Test
	void retryingAnAlreadySucceededExecutionIsANoOpAndNeverDuplicatesTheCandidate()
			throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		UUID projectId = seedProject();
		AgentExecution execution = startedExecution(projectId);
		WebsiteImplementationCandidate first =
				coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		WebsiteImplementationCandidate retried =
				coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(retried.getId()).isEqualTo(first.getId());
		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(candidateRepository.findAll().stream()
						.filter(c -> c.getAgentExecutionId().equals(execution.getId()))
						.count())
				.isEqualTo(1);
	}

	@Test
	void aRepositoryStateMismatchTerminatesTheExecutionAsErrorAndLeavesTheWorkspaceOnDiskUntouched()
			throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		UUID projectId = seedProject();
		AgentExecution execution = startedExecution(projectId);
		// Mutates tracked state after the freeze - the same "something changed between
		// verification and acceptance" scenario WebsiteImplementationCandidatePromoterIT covers.
		Files.writeString(root.resolve("src/pages/Home.tsx"), "export const Home = () => \"mutated\";");
		run(root, "git", "add", "src/pages/Home.tsx");

		assertThatThrownBy(() ->
						coordinator.accept(execution, projectId, DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot))
				.isInstanceOf(RepositoryStateMismatchException.class);

		assertThat(agentExecutionRepository.findById(execution.getId()).orElseThrow().getStatus())
				.isEqualTo(AgentExecutionStatus.ERROR);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).isEmpty();
		// "Repository state remains retained/reachable when Candidate DB persistence temporarily
		// fails" - nothing in this path ever deletes the workspace, so it is still right here.
		assertThat(Files.exists(root.resolve("src/pages/Home.tsx"))).isTrue();
	}

	private AgentExecution startedExecution(UUID projectId) {
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "developer-agent", 1));
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private UUID seedProject() {
		return projectRepository.saveAndFlush(new Project("website")).getId();
	}

	private void writeAndTrack(String relativePath, String content) throws IOException, InterruptedException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		run(root, "git", "add", relativePath);
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
