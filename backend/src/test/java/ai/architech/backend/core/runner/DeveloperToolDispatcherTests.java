package ai.architech.backend.core.runner;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.developer.tooling.DeveloperToolCapabilityProfile;
import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.core.toolexecution.ToolCapability;
import ai.architech.backend.core.toolexecution.ToolExecutionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Unit-level proof of every dispatch branch (AIW-184) - no Spring context needed, real git/filesystem via {@code @TempDir}. */
class DeveloperToolDispatcherTests {

	private final DeveloperToolDispatcher dispatcher = new DeveloperToolDispatcher();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@TempDir
	Path root;

	private Workspace workspace;
	private DeveloperToolCapabilityProfile fullyGrantedProfile;

	@BeforeEach
	void setUp() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
		workspace = new Workspace(root);
		fullyGrantedProfile = new DeveloperToolCapabilityProfile(
				"test-profile",
				new DeveloperToolCapabilityProfile.Filesystem(
						List.of("list", "read", "search", "write", "patch", "mkdir", "move", "delete"), true, List.of(".git", "runner-metadata")),
				new DeveloperToolCapabilityProfile.ProjectExecution(List.of("install", "typecheck", "lint", "test", "build"), false),
				true,
				new DeveloperToolCapabilityProfile.Git(List.of("status", "diff", "diff-stat"), List.of()),
				true,
				new DeveloperToolCapabilityProfile.Network(false, true),
				new DeveloperToolCapabilityProfile.Security(true, false, false, false));
	}

	@Test
	void writesReadsAndSearchesAFile() {
		DeveloperToolCallOutcome write = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("write", "notes.txt", "content", "hello world"));
		assertThat(write.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		assertThat(write.capability()).isEqualTo(ToolCapability.FILESYSTEM);

		DeveloperToolCallOutcome read = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("read", "notes.txt"));
		assertThat(read.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		assertThat(read.detail()).isEqualTo("hello world");

		DeveloperToolCallOutcome search = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("search", ".", "pattern", "world"));
		assertThat(search.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		assertThat(search.detail()).contains("notes.txt");
	}

	@Test
	void listsPatchesMkdirsMovesAndDeletes() {
		dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("write", "a.txt", "content", "old"));

		DeveloperToolCallOutcome list = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("list", "."));
		assertThat(list.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		assertThat(list.detail()).contains("a.txt");

		ObjectNode patchInput = objectMapper.createObjectNode();
		patchInput.put("operation", "patch").put("path", "a.txt").put("oldContent", "old").put("newContent", "new");
		DeveloperToolCallOutcome patch = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", patchInput);
		assertThat(patch.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		assertThat(dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("read", "a.txt")).detail()).isEqualTo("new");

		DeveloperToolCallOutcome mkdir = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("mkdir", "sub"));
		assertThat(mkdir.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);

		ObjectNode moveInput = objectMapper.createObjectNode();
		moveInput.put("operation", "move").put("path", "a.txt").put("destination", "sub/a.txt");
		DeveloperToolCallOutcome move = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", moveInput);
		assertThat(move.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);

		DeveloperToolCallOutcome delete = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("delete", "sub/a.txt"));
		assertThat(delete.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
	}

	@Test
	void deniesAFilesystemOperationNotInTheProfilesAllowlist() {
		DeveloperToolCapabilityProfile readOnlyProfile = new DeveloperToolCapabilityProfile(
				"read-only",
				new DeveloperToolCapabilityProfile.Filesystem(List.of("list", "read"), true, List.of(".git", "runner-metadata")),
				fullyGrantedProfile.projectExecution(),
				true,
				fullyGrantedProfile.git(),
				true,
				fullyGrantedProfile.network(),
				fullyGrantedProfile.security());

		DeveloperToolCallOutcome outcome = dispatcher.dispatch(readOnlyProfile, workspace, "filesystem", filesystemInput("write", "a.txt", "content", "x"));

		assertThat(outcome.status()).isEqualTo(ToolExecutionStatus.DENIED);
	}

	@Test
	void deniesAnUnknownFilesystemOperation() {
		DeveloperToolCallOutcome outcome = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("format-disk", "a.txt"));

		assertThat(outcome.status()).isEqualTo(ToolExecutionStatus.DENIED);
	}

	@Test
	void deniesAProtectedPathEvenWhenTheOperationItselfIsAllowed() {
		DeveloperToolCallOutcome outcome = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("read", ".git/config"));

		assertThat(outcome.status()).isEqualTo(ToolExecutionStatus.DENIED);
	}

	@Test
	void reportsAGenuineFilesystemFailureAsFailedNeverDenied() {
		DeveloperToolCallOutcome outcome = dispatcher.dispatch(fullyGrantedProfile, workspace, "filesystem", filesystemInput("read", "does-not-exist.txt"));

		assertThat(outcome.status()).isEqualTo(ToolExecutionStatus.FAILED);
	}

	@Test
	void runsAnApprovedProjectExecutionTaskAndReportsANonzeroExitAsFailedNeverError() throws IOException {
		Files.writeString(root.resolve("package.json"), "{\"name\":\"tmp\",\"scripts\":{\"build\":\"exit 1\"}}");

		ObjectNode taskInput = objectMapper.createObjectNode();
		taskInput.put("task", "build");
		DeveloperToolCallOutcome outcome = dispatcher.dispatch(fullyGrantedProfile, workspace, "project_execution", taskInput);

		assertThat(outcome.status()).isEqualTo(ToolExecutionStatus.FAILED);
		assertThat(outcome.capability()).isEqualTo(ToolCapability.PROJECT_EXECUTION);
	}

	@Test
	void deniesAProjectExecutionTaskNotInTheProfilesAllowlist() {
		DeveloperToolCapabilityProfile noBuildProfile = new DeveloperToolCapabilityProfile(
				"no-build",
				fullyGrantedProfile.filesystem(),
				new DeveloperToolCapabilityProfile.ProjectExecution(List.of("install"), false),
				true,
				fullyGrantedProfile.git(),
				true,
				fullyGrantedProfile.network(),
				fullyGrantedProfile.security());
		ObjectNode taskInput = objectMapper.createObjectNode();
		taskInput.put("task", "build");

		DeveloperToolCallOutcome outcome = dispatcher.dispatch(noBuildProfile, workspace, "project_execution", taskInput);

		assertThat(outcome.status()).isEqualTo(ToolExecutionStatus.DENIED);
	}

	@Test
	void deniesLocalRuntimeAndAnUnknownProjectExecutionTask() {
		ObjectNode localRuntimeInput = objectMapper.createObjectNode();
		localRuntimeInput.put("task", "local-runtime");
		assertThat(dispatcher.dispatch(fullyGrantedProfile, workspace, "project_execution", localRuntimeInput).status())
				.isEqualTo(ToolExecutionStatus.DENIED);

		ObjectNode unknownInput = objectMapper.createObjectNode();
		unknownInput.put("task", "deploy-to-production");
		assertThat(dispatcher.dispatch(fullyGrantedProfile, workspace, "project_execution", unknownInput).status())
				.isEqualTo(ToolExecutionStatus.DENIED);
	}

	@Test
	void allowsAGrantedGitInspectionSubcommandAndDeniesEverythingElse() {
		ObjectNode statusInput = objectMapper.createObjectNode();
		statusInput.put("command", "status");
		DeveloperToolCallOutcome status = dispatcher.dispatch(fullyGrantedProfile, workspace, "git_inspect", statusInput);
		assertThat(status.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
		assertThat(status.capability()).isEqualTo(ToolCapability.GIT_INSPECTION);

		ObjectNode pushInput = objectMapper.createObjectNode();
		pushInput.put("command", "push");
		assertThat(dispatcher.dispatch(fullyGrantedProfile, workspace, "git_inspect", pushInput).status()).isEqualTo(ToolExecutionStatus.DENIED);
	}

	@Test
	void deniesAndRecordsAnEntirelyUnknownTopLevelTool() {
		DeveloperToolCallOutcome outcome = dispatcher.dispatch(fullyGrantedProfile, workspace, "deploy_to_prod", objectMapper.createObjectNode());

		assertThat(outcome.status()).isEqualTo(ToolExecutionStatus.DENIED);
		assertThat(outcome.capability()).isEqualTo(ToolCapability.FILESYSTEM);
	}

	private ObjectNode filesystemInput(String operation, String path) {
		return objectMapper.createObjectNode().put("operation", operation).put("path", path);
	}

	private ObjectNode filesystemInput(String operation, String path, String extraKey, String extraValue) {
		return filesystemInput(operation, path).put(extraKey, extraValue);
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
