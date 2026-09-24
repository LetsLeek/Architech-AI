package ai.architech.backend.core.validation;

/** {@code validator} names which stage of the pipeline raised this (schema/target-identity/reference/anchor/functional-binding/integration-contract/unresolved-issue/blocker). */
public record DeveloperResultValidationIssue(String validator, String ref, String reason) {}
