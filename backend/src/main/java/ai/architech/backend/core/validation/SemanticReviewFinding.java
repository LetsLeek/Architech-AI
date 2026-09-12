package ai.architech.backend.core.validation;

/**
 * One finding from {@link DesignProposalSetSemanticReviewer}. {@code proposalRef} is the
 * proposal's own {@code localRef} the finding concerns, or {@code null} for a finding that
 * spans the whole candidate (e.g. "all three proposals are near-identical"). {@code blocking}
 * is attached by {@link SemanticReviewPolicy} from {@code category} - the reviewer's own AI
 * call reports only what it found, never whether that finding should block; that's a platform
 * policy decision, not a model judgment.
 */
public record SemanticReviewFinding(String proposalRef, String category, String message, boolean blocking) {}
