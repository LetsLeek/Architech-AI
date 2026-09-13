package ai.architech.backend.core.handoff;

import java.time.Instant;

/**
 * A stable identity for the exact git-tracked workspace state at the moment Developer write
 * authority was frozen (AIW-154). {@code snapshotId} is a deterministic content digest over every
 * git-tracked file's path and bytes - two snapshots are equal exactly when the tracked tree's
 * content is identical, and different the instant anything tracked changes (a new commit, a
 * newly tracked file, an edited one). Untracked/gitignored transient output (node_modules, dist,
 * ...) never affects it, since only {@code git ls-files}-tracked paths are ever hashed - the same
 * boundary {@code SecretScanGate} draws for the same reason (AIW-158).
 */
public record FrozenHandoffSnapshot(String snapshotId, Instant frozenAt) {}
