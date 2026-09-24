package ai.architech.backend.core.validation;

/**
 * One {@code evidenceRefs} entry's binding problem, named against the specific reference string
 * it came from so a caller can report exactly which claimed Evidence item failed and why
 * (AIW-170).
 */
public record EvidenceReferenceProblem(String evidenceRef, String problem) {}
