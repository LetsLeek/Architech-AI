package ai.architech.backend.core.rule;

/**
 * A parsed {@code rule.yaml} plus the textual rule content from its sibling
 * {@code RULE.md}. Project-type-agnostic, same as {@code AgentDefinition} - nothing here
 * knows which project type a rule belongs to.
 */
public record RuleDefinition(int schemaVersion, String id, String name, int version, String description, String content) {}
