package ai.architech.backend.core.qa.invariants;

/** One invariant problem in a semantic finding candidate, before it is ever persisted as a real {@code CandidateFinding} (AIW-174). */
public record FindingInvariantIssue(String path, String message) {}
