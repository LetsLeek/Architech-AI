package ai.architech.backend.core.sandbox;

import java.time.Duration;
import java.util.Optional;

/**
 * Runs exactly one of the three allowed Git inspection operations ({@link GitCommand}) scoped to
 * one {@link Workspace}. There is no method here that accepts an arbitrary subcommand string and
 * forwards it to a process - {@link #run(GitCommand)} only ever accepts a {@link GitCommand}
 * enum constant, and {@link #runIfAllowed(String)} denies anything that doesn't map to one
 * before a process is ever started, never by pattern-matching and rejecting a dangerous one.
 */
public final class GitInspectionCapability {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final Workspace workspace;

	public GitInspectionCapability(Workspace workspace) {
		this.workspace = workspace;
	}

	public ProcessResult run(GitCommand command) {
		return ProcessRunner.run(workspace, command.processArgs(), TIMEOUT);
	}

	/** Returns empty (never starts a process) when {@code requestedSubcommand} is not allowed. */
	public Optional<ProcessResult> runIfAllowed(String requestedSubcommand) {
		return GitCommand.fromRequestedSubcommand(requestedSubcommand).map(this::run);
	}
}
