package ai.architech.backend.core.validation;

/** One cited ref that doesn't belong to the evidence snapshot it was checked against. */
public record SourceRefValidationIssue(String ref, String reason) {}
