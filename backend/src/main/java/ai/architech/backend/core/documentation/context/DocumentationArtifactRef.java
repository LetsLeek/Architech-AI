package ai.architech.backend.core.documentation.context;

/**
 * Mirrors {@code documentation-context.schema.json}'s {@code artifact-ref.schema.json} object
 * ({@code {artifactType, artifactVersionRef}}) - every field inside {@code authoritySnapshot} is
 * one of these, not a plain ref string (a mismatch corrected here after being introduced in
 * AIW-189: that ticket's own fixtures were never checked against the real cross-file {@code $ref}
 * before this ticket needed to actually serialize a schema-valid {@code authoritySnapshot}).
 */
public record DocumentationArtifactRef(String artifactType, String artifactVersionRef) {}
