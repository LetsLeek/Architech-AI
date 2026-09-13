package ai.architech.backend.core.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pure command-mapping tests - deliberately does not shell out to a real {@code npm} process
 * here (that would make the backend test suite network/toolchain-dependent). AIW-138's own
 * Development Base scaffold already proves every one of these exact commands (install/typecheck/
 * lint/test/build/dev) works end to end against a real project; this class's own job is only the
 * dispatch/containment mechanism, which {@link WorkspaceTests}/{@link WorkspaceFileSystemTests}/
 * {@link GitInspectionCapabilityTests} already prove works against real processes.
 */
class ProjectExecutionCapabilityTests {

	@Test
	void mapsEveryApprovedTaskToItsExactFixedCommand() {
		assertThat(ProjectExecutionCapability.commandFor(ProjectExecutionTask.INSTALL)).isEqualTo(List.of("npm", "ci"));
		assertThat(ProjectExecutionCapability.commandFor(ProjectExecutionTask.TYPECHECK))
				.isEqualTo(List.of("npm", "run", "typecheck"));
		assertThat(ProjectExecutionCapability.commandFor(ProjectExecutionTask.LINT)).isEqualTo(List.of("npm", "run", "lint"));
		assertThat(ProjectExecutionCapability.commandFor(ProjectExecutionTask.TEST)).isEqualTo(List.of("npm", "run", "test"));
		assertThat(ProjectExecutionCapability.commandFor(ProjectExecutionTask.BUILD)).isEqualTo(List.of("npm", "run", "build"));
		assertThat(ProjectExecutionCapability.commandFor(ProjectExecutionTask.LOCAL_RUNTIME))
				.isEqualTo(List.of("npm", "run", "dev"));
	}

	@Test
	void rejectsRunningLocalRuntimeThroughTheBoundedRunMethod(@TempDir Path root) {
		ProjectExecutionCapability capability = new ProjectExecutionCapability(new Workspace(root));

		assertThatThrownBy(() -> capability.run(ProjectExecutionTask.LOCAL_RUNTIME)).isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * No package.json exists in this bare temp workspace, so npm fails fast (no network, no
	 * install needed) - proving {@link ProjectExecutionCapability#run} genuinely starts a real
	 * process scoped to the workspace and reports its real (here, non-zero) result, without this
	 * test needing a full installed scaffold. AIW-138's own scaffold already proves every one of
	 * these commands succeeds against a real project.
	 */
	@Test
	void runActuallyStartsARealProcessScopedToTheWorkspace(@TempDir Path root) {
		ProjectExecutionCapability capability = new ProjectExecutionCapability(new Workspace(root));

		ProcessResult result = capability.run(ProjectExecutionTask.LINT);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.stderr()).contains("package.json");
	}

	@Test
	void startLocalRuntimeReturnsALiveProcessHandleTheCallerOwns(@TempDir Path root)
			throws InterruptedException {
		ProjectExecutionCapability capability = new ProjectExecutionCapability(new Workspace(root));

		Process process = capability.startLocalRuntime();
		try {
			assertThat(process.pid()).isPositive();
		} finally {
			process.destroyForcibly();
			process.waitFor();
		}
	}

	@Test
	void noApprovedTaskInvokesARawNetworkTool() {
		// network.rawOutbound: false - every fixed command here is an npm script invocation;
		// npm's own registry traffic is the sole authorized network path (dependencies is its
		// own managed capability, AIW-140), never a raw curl/wget/nc/ssh call this class itself
		// could be asked to make.
		var networkTools = List.of("curl", "wget", "nc", "ssh", "scp", "ftp");
		for (ProjectExecutionTask task : ProjectExecutionTask.values()) {
			assertThat(ProjectExecutionCapability.commandFor(task)).noneMatch(networkTools::contains);
		}
	}

	@Test
	void everyApprovedTaskIsOneOfTheSixNamesTheProfileLists() {
		assertThat(ProjectExecutionTask.values())
				.containsExactlyInAnyOrder(
						ProjectExecutionTask.INSTALL,
						ProjectExecutionTask.TYPECHECK,
						ProjectExecutionTask.LINT,
						ProjectExecutionTask.TEST,
						ProjectExecutionTask.BUILD,
						ProjectExecutionTask.LOCAL_RUNTIME);
	}
}
