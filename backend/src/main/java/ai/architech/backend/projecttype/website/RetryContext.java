package ai.architech.backend.projecttype.website;

/**
 * Matches {@code developer-execution-input.v1}'s {@code executionContext.retryContext} exactly.
 * A structured retry hint only - never new customer/design authority (SKILL.md's own "Retry
 * behavior" section) - supplied by whatever Workflow logic decided to retry, never invented by
 * the assembler itself.
 */
public record RetryContext(String priorAgentExecutionRef, String retryReasonCode, String failureEvidenceSummary) {}
