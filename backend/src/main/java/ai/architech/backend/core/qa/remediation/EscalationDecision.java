package ai.architech.backend.core.qa.remediation;

/**
 * One deterministic escalation-routing decision (AIW-181) - {@code reasonCode} is the structured,
 * auditable reason ("All retry/remediation/escalation decisions remain auditable with structured
 * reason codes"), never a free-text explanation.
 */
public record EscalationDecision(EscalationRoute route, String reasonCode) {}
