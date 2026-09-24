package ai.architech.backend.core.developer.tooling;

import ai.architech.backend.core.sandbox.FilesystemCapability;
import ai.architech.backend.core.sandbox.ProjectExecutionTask;
import java.util.List;

/**
 * A resolved-per-execution Website Developer V1 tool capability grant (AIW-184) - {@code
 * developer-execution-input.v1}'s {@code technicalContext.toolCapabilityProfileRef} names exactly
 * one of these by {@link #id()}. Unlike QA's own {@code QaToolCapabilityProfile} (entirely
 * read-only by construction), this type genuinely grants filesystem write, bounded project
 * execution and bounded git inspection - the allowlists here are the actual hard dispatch filter
 * the new tool-calling loop checks before ever touching the sandbox, never a suggestion the loop
 * could override.
 */
public record DeveloperToolCapabilityProfile(
		String id,
		Filesystem filesystem,
		ProjectExecution projectExecution,
		boolean dependenciesManagedCapability,
		Git git,
		boolean browserLocalRuntimeOnly,
		Network network,
		Security security) {

	/** {@code operation} is one of {@link FilesystemCapability}'s own lowercase names (e.g. {@code "write"}). */
	public boolean allowsFilesystemOperation(FilesystemCapability operation) {
		return filesystem.allowed().contains(operation.name().toLowerCase());
	}

	public boolean allowsProjectExecutionTask(ProjectExecutionTask task) {
		return projectExecution.approvedTasks().contains(yamlNameOf(task));
	}

	public boolean allowsGitSubcommand(String requestedSubcommand) {
		return git.allowed().contains(requestedSubcommand);
	}

	private static String yamlNameOf(ProjectExecutionTask task) {
		return task.name().toLowerCase().replace('_', '-');
	}

	public record Filesystem(List<String> allowed, boolean workspaceOnly, List<String> protectedPaths) {}

	public record ProjectExecution(List<String> approvedTasks, boolean unrestrictedShell) {}

	public record Git(List<String> allowed, List<String> prohibited) {}

	public record Network(boolean rawOutbound, boolean allowedOnlyThroughAuthorizedCapabilities) {}

	/** One field per {@code tool-capability-profile.v1.yaml}'s own named prohibition - required to hold the safe value, enforced by {@link DeveloperToolCapabilityProfileLoader}. */
	public record Security(boolean nonRoot, boolean dockerSocket, boolean cloudCredentials, boolean platformSecrets) {}
}
