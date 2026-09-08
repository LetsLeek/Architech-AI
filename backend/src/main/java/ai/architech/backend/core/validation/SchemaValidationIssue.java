package ai.architech.backend.core.validation;

/** {@code path} is the JSON Pointer-style instance location the error occurred at (e.g. "$.business.name"). */
public record SchemaValidationIssue(String path, String message) {}
