package ai.architech.backend.core.sandbox;

import java.util.List;
import java.util.Optional;

/**
 * The complete, closed set of Git operations {@code tool-capability-profile.v1.yaml} allows
 * ({@code status}, {@code diff}, {@code diff-stat}) - inspection only. Everything the profile
 * lists as prohibited (branch, checkout/switch, merge, rebase, commit-by-agent, push,
 * force-push, remote-credential-access) has deliberately no corresponding constant here: there
 * is no enum value a caller could pass to make {@link GitInspectionCapability} run one of them.
 */
public enum GitCommand {
	STATUS(List.of("git", "status", "--porcelain")),
	DIFF(List.of("git", "diff")),
	DIFF_STAT(List.of("git", "diff", "--stat"));

	private final List<String> processArgs;

	GitCommand(List<String> processArgs) {
		this.processArgs = processArgs;
	}

	List<String> processArgs() {
		return processArgs;
	}

	/**
	 * Maps a requested subcommand name to its allowed {@link GitCommand}, or empty if the
	 * requested name is not one of the three allowed operations (including every prohibited
	 * operation the profile names) - the denial happens here, before any process is ever
	 * started.
	 */
	public static Optional<GitCommand> fromRequestedSubcommand(String requested) {
		return switch (requested) {
			case "status" -> Optional.of(STATUS);
			case "diff" -> Optional.of(DIFF);
			case "diff-stat", "diffStat" -> Optional.of(DIFF_STAT);
			default -> Optional.empty();
		};
	}
}
