package ai.architech.backend.core.validation;

/**
 * {@code validator} names which stage of {@link DocumentationCandidateStructureValidator} raised
 * this ("schema"/"identity"/"structure") - mirrors {@link DeveloperResultValidationIssue}'s own
 * shape, the direct precedent for a post-generation candidate validation issue (as opposed to
 * {@link PreExecutionValidationIssue}, which names a pre-execution/preflight gate instead).
 */
public record DocumentationCandidateValidationIssue(String validator, String ref, String reason) {}
