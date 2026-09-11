package ai.architech.backend.core.agent;

/**
 * One declared output of an agent. {@code schema} is the raw relative path exactly as
 * written in agent.yaml (e.g. "../../schemas/customer-profile.schema.json") - kept for
 * reference/debugging. {@code schemaContent} is that same schema file's actual text,
 * resolved once at load time by {@link AgentDefinitionLoader} relative to agent.yaml's own
 * location (the same way it already resolves AGENT.md) - callers that need the schema
 * (prompt assembly, validation) use this directly and never need to re-derive a classpath
 * location from the raw path themselves.
 */
public record AgentArtifactOutput(String type, String schema, String schemaContent, boolean required) {}
