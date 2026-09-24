package ai.architech.backend.core.evidence;

import java.util.UUID;

/**
 * One piece of readable evidence content, traceable back to the exact input row it came
 * from. {@code id} here is the underlying input's own id - not yet an opaque platform
 * reference (that's SourceRef generation, AIW-41, layered on top of this).
 */
public record SourceItem(UUID id, SourceOrigin origin, String content) {}
