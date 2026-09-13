package ai.architech.backend.core.verification;

/**
 * A fatal browser/runtime error or critical asset load failure observed during local route/
 * navigation smoke - deliberately its own type, never merged into {@link NetworkPolicyFinding}:
 * AIW-157's own AC requires these "captured separately from network-policy findings" (a page
 * throwing an uncaught exception is not a network-policy violation, and an unauthorized external
 * request is not itself a runtime error).
 */
public record BrowserRuntimeIssue(String source, String message) {}
