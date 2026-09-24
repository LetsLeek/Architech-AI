package ai.architech.backend.core.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
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
 * A genuine end-to-end proof against a real local git repository (mirroring {@code
 * HandoffFreezeGateTests}/{@code SecretScanGateTests}' own precedent) that persistence and the
 * frozen snapshot identity actually agree with each other, not just with a mock.
 */
@SpringBootTest
@Transactional
class WebsiteImplementationCandidatePromoterIT {

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
	void persistsAVerifiedCandidateWithARepositoryStateRefEqualToTheEvaluatedSnapshot() throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		UUID projectId = seedProject();
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "developer-agent", 1));

		WebsiteImplementationCandidate candidate = promoter.promote(
				projectId, execution.getId(), DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(candidate.getRepositoryStateRef()).isEqualTo(snapshot.snapshotId());
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).isPresent();
	}

	@Test
	void aRetryAfterSuccessfulPersistenceReturnsTheSameCandidateRatherThanCreatingASecondOne() throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		UUID projectId = seedProject();
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "developer-agent", 1));

		WebsiteImplementationCandidate first =
				promoter.promote(projectId, execution.getId(), DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);
		WebsiteImplementationCandidate retried =
				promoter.promote(projectId, execution.getId(), DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot);

		assertThat(retried.getId()).isEqualTo(first.getId());
	}

	@Test
	void refusesToPersistWhenTheTrackedTreeChangedSinceTheSnapshotWasTaken() throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", "export const Home = () => null;");
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		UUID projectId = seedProject();
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "developer-agent", 1));
		// Mutates tracked state directly, bypassing the frozen Workspace check entirely - exactly
		// the "something changed between verification and persistence" scenario this guards.
		Files.writeString(root.resolve("src/pages/Home.tsx"), "export const Home = () => \"mutated\";");
		run(root, "git", "add", "src/pages/Home.tsx");

		assertThatThrownBy(() -> promoter.promote(
						projectId, execution.getId(), DEVELOPER_RESULT, EXECUTION_INPUT, workspace, snapshot))
				.isInstanceOf(RepositoryStateMismatchException.class);
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
