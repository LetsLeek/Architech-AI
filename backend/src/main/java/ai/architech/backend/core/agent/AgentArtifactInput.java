package ai.architech.backend.core.agent;

/**
 * One declared input of an agent - the counterpart to {@link AgentArtifactOutput}. {@code
 * schemaContent} is resolved eagerly at load time the same way an output's schema is, so a
 * caller validating a supplied input artifact never needs to re-derive a classpath location
 * from the raw path itself.
 *
 * <p>Introduced by the Designer Agent (AIW-116) - the first agent in this project whose
 * inputs are prior canonical artifacts (the Requirements Agent's own outputs) rather than a
 * raw Source Context evidence snapshot.
 */
public record AgentArtifactInput(String type, String schema, String schemaContent, boolean required) {}
