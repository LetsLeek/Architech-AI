package ai.architech.backend.core.handoff;

import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The Runner-owned freeze/mutation-detection boundary AIW-154 describes: freezes a
 * {@link Workspace}'s Developer write authority and computes a {@link FrozenHandoffSnapshot} of
 * its exact git-tracked content in the same operation, so result validation and authoritative
 * Runner verification can both prove they evaluated the identical frozen state by comparing
 * against the same {@code snapshotId} - and so any later mutation (whether an authorized
 * correction or an unexpected write by a verification tool) is detectable by simply recomputing
 * and comparing, never by trusting that nothing happened.
 *
 * <p>Only an authorized correction phase (see {@code AgentExecution#authorizeCorrectionCycle},
 * AIW-150) may call {@link #unfreezeForCorrection} - reopening write authority is deliberately a
 * separate, explicit call from freezing, never a side effect of anything else. Re-freezing after
 * a correction always produces a new {@link FrozenHandoffSnapshot} with a different
 * {@code snapshotId} whenever tracked content actually changed - invalidating whatever prior
 * acceptance evidence was computed against the old snapshot is the calling Runner's own
 * responsibility once that pipeline exists; this class only guarantees the identity itself is
 * trustworthy.
 */
@Component
public class HandoffFreezeGate {

	/** Freezes the workspace and returns a snapshot of its current git-tracked content. */
	public FrozenHandoffSnapshot freeze(Workspace workspace) {
		String snapshotId = computeSnapshotId(workspace.root());
		workspace.freeze();
		return new FrozenHandoffSnapshot(snapshotId, Instant.now());
	}

	/** Reopens write authority - callers must already have confirmed a correction cycle was authorized. */
	public void unfreezeForCorrection(Workspace workspace) {
		workspace.unfreeze();
	}

	/** True when the workspace's current git-tracked content still matches exactly what {@code snapshot} recorded. */
	public boolean matchesCurrentState(Workspace workspace, FrozenHandoffSnapshot snapshot) {
		return computeSnapshotId(workspace.root()).equals(snapshot.snapshotId());
	}

	private String computeSnapshotId(Path repositoryRoot) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}

		for (String relativePath : trackedFiles(repositoryRoot).stream().sorted().toList()) {
			digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(fileBytes(repositoryRoot.resolve(relativePath)));
			digest.update((byte) 0);
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	private byte[] fileBytes(Path file) {
		try {
			return Files.readAllBytes(file);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read tracked file " + file, e);
		}
	}

	private List<String> trackedFiles(Path repositoryRoot) {
		Process process;
		try {
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			// Fixed literal git subcommand, never caller-supplied text - same justification as
			// core.verification.SecretScanGate / core.sandbox.ProcessRunner / core.repository.GitCommandRunner.
			process = new ProcessBuilder("git", "ls-files").directory(repositoryRoot.toFile()).start();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to list tracked files in " + repositoryRoot, e);
		}

		String output;
		try {
			output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			process.getErrorStream().readAllBytes();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Failed to read tracked file list for " + repositoryRoot, e);
		}

		return output.lines().filter(line -> !line.isBlank()).toList();
	}
}
