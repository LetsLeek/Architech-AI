package ai.architech.backend.core.evidence;

/**
 * What an agent actually sees for one piece of evidence: the opaque ref and the content -
 * deliberately no raw input id, so the agent has nothing to invent, rename, or derive a
 * "real" identifier from.
 */
public record ReferencedSourceItem(String sourceRef, SourceOrigin origin, String content) {}
