package ai.architech.backend.core.agent;

import java.util.List;

/**
 * A parsed {@code agent.yaml} plus the actual role/persona text from its sibling
 * {@code AGENT.md}. Project-type-agnostic: nothing here knows or cares which project type
 * (e.g. "website") an agent belongs to - that's just where its file happens to live on disk.
 *
 * <p>{@code inputs} is empty for an agent (like requirements-agent) whose only input is a raw
 * Source Context evidence snapshot rather than prior canonical artifacts - {@code inputs} is
 * optional in agent.yaml itself, unlike the always-required {@code outputs}.
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
		AgentInputs inputs,
		AgentOutputs outputs,
		String roleContent) {}
