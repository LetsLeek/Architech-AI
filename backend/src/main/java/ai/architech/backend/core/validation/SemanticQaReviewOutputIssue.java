package ai.architech.backend.core.validation;

/** One identity-mismatch problem in a {@code semantic-qa-review-output} candidate (AIW-173). */
public record SemanticQaReviewOutputIssue(String path, String message) {}
