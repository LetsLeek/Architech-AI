package ai.architech.backend.core.agent;

import java.util.List;

/**
 * A parsed {@code agent.yaml}. Project-type-agnostic: nothing here knows or cares which
 * project type (e.g. "website") an agent belongs to - that's just where its file happens
 * to live on disk.
 */
public record AgentDefinition(
		int schemaVersion,
		String id,
		String name,
		int version,
		String description,
		String modelProfile,
		AgentLimits limits,
		List<String> skills,
		List<String> rules,
		AgentOutputs outputs) {}
