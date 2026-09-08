package ai.architech.backend.core.validation;

public record CrossArtifactValidationIssue(String artifactType, String ref, String reason) {}
