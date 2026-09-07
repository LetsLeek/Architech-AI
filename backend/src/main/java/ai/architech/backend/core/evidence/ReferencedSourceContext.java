package ai.architech.backend.core.evidence;

import java.util.List;
import java.util.UUID;

/** The agent-facing view of a SourceContext, once every item has an assigned opaque ref. */
public record ReferencedSourceContext(UUID evidenceSnapshotId, List<ReferencedSourceItem> items) {}
