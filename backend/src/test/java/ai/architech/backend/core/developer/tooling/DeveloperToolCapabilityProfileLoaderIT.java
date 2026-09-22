package ai.architech.backend.core.developer.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.sandbox.FilesystemCapability;
import ai.architech.backend.core.sandbox.ProjectExecutionTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real-classpath proof against the shipped {@code website-developer-tools-v1} profile (AIW-184). */
@SpringBootTest
class DeveloperToolCapabilityProfileLoaderIT {

	@Autowired
	private DeveloperToolCapabilityProfileLoader loader;

	@Test
	void resolvesTheFrozenWebsiteDeveloperToolsProfileById() {
		DeveloperToolCapabilityProfile profile = loader.resolve("website-developer-tools-v1");

		assertThat(profile.id()).isEqualTo("website-developer-tools-v1");
		assertThat(profile.filesystem().allowed()).containsExactlyInAnyOrder(
				"list", "read", "search", "write", "patch", "mkdir", "move", "delete");
		assertThat(profile.projectExecution().approvedTasks()).containsExactlyInAnyOrder(
				"install", "typecheck", "lint", "test", "build", "local-runtime");
		assertThat(profile.git().allowed()).containsExactlyInAnyOrder("status", "diff", "diff-stat");
	}

	@Test
	void everyFilesystemCapabilityAndApprovedProjectExecutionTaskIsGranted() {
		DeveloperToolCapabilityProfile profile = loader.resolve("website-developer-tools-v1");

		for (FilesystemCapability capability : FilesystemCapability.values()) {
			assertThat(profile.allowsFilesystemOperation(capability)).as(capability.name()).isTrue();
		}
		for (ProjectExecutionTask task : ProjectExecutionTask.values()) {
			assertThat(profile.allowsProjectExecutionTask(task)).as(task.name()).isTrue();
		}
	}

	@Test
	void grantsOnlyTheThreeReadOnlyGitInspectionSubcommandsNeverAMutatingOne() {
		DeveloperToolCapabilityProfile profile = loader.resolve("website-developer-tools-v1");

		assertThat(profile.allowsGitSubcommand("status")).isTrue();
		assertThat(profile.allowsGitSubcommand("diff")).isTrue();
		assertThat(profile.allowsGitSubcommand("diff-stat")).isTrue();
		assertThat(profile.allowsGitSubcommand("push")).isFalse();
		assertThat(profile.allowsGitSubcommand("force-push")).isFalse();
		assertThat(profile.allowsGitSubcommand("commit-by-agent")).isFalse();
		assertThat(profile.allowsGitSubcommand("branch")).isFalse();
	}

	@Test
	void theFrozenProfileSatisfiesItsOwnRequiredSafetyPosture() {
		DeveloperToolCapabilityProfile profile = loader.resolve("website-developer-tools-v1");

		assertThat(profile.security().nonRoot()).isTrue();
		assertThat(profile.security().dockerSocket()).isFalse();
		assertThat(profile.security().cloudCredentials()).isFalse();
		assertThat(profile.security().platformSecrets()).isFalse();
		assertThat(profile.projectExecution().unrestrictedShell()).isFalse();
		assertThat(profile.network().rawOutbound()).isFalse();
	}

	@Test
	void rejectsAnUnknownProfileId() {
		assertThatThrownBy(() -> loader.resolve("website-developer-made-up-v1"))
				.isInstanceOf(DeveloperToolCapabilityProfileNotFoundException.class);
	}
}
