package ai.architech.backend.core.validation;

/** {@code proposalRef} is the proposal's own {@code localRef} - {@code null} when an issue is not scoped to one specific proposal (e.g. malformed top-level JSON). */
public record DesignProposalSetValidationIssue(String proposalRef, String path, String reason) {}
