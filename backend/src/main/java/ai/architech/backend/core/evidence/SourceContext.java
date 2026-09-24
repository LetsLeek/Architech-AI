package ai.architech.backend.core.evidence;

import java.util.List;
import java.util.UUID;

/**
 * The evidence for one AgentExecution attempt, tied to exactly one {@link EvidenceSnapshot}
 * and containing only what that snapshot allows - nothing pulled in from elsewhere.
 *
 * <p>This is untrusted data: whatever an agent's Skill/Rule instructions say about treating
 * source content as non-authoritative applies to every item here. Nothing in this class
 * parses or executes anything from {@code content} - it is only ever read as opaque text
 * when assembling a model prompt (that assembly is the Runner's job, not this one's).
 */
public record SourceContext(UUID evidenceSnapshotId, List<SourceItem> items) {}
