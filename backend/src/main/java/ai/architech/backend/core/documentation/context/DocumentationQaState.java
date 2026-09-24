package ai.architech.backend.core.documentation.context;

/**
 * Mirrors {@code documentation-context.schema.json}'s {@code qaState} object (AIW-189).
 * {@code qaProfile} is always the literal {@code "FULL_RELEASE"} - the schema itself declares it
 * a {@code const}, the one QA profile scope Documentation ever consumes.
 */
public record DocumentationQaState(String qaResultRef, String candidateRef, String qaProfile, String gate) {}
