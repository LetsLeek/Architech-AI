package ai.architech.backend.core.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds the new multi-turn tool-calling loop (AIW-184) - Workflow/Core-owned, never an agent's
 * own decision, mirroring {@link RunnerProperties}'s own idiom. {@code maxTurns} bounds the
 * number of model round-trips within one execution (each turn may request several tool calls
 * at once); {@code maxToolCalls} separately bounds the total number of individual tool calls
 * dispatched across every turn, so a single turn requesting an unreasonable number of calls at
 * once can't exhaust the budget in one step while still reporting only one turn used. Exhausting
 * either ends the execution {@code FAILED} - never an unbounded loop.
 */
@ConfigurationProperties(prefix = "architech.developer.tool-loop")
public record DeveloperToolLoopProperties(int maxTurns, int maxToolCalls) {

	public DeveloperToolLoopProperties {
		if (maxTurns < 1) {
			throw new IllegalStateException("architech.developer.tool-loop.max-turns must be at least 1, was " + maxTurns);
		}
		if (maxToolCalls < 1) {
			throw new IllegalStateException("architech.developer.tool-loop.max-tool-calls must be at least 1, was " + maxToolCalls);
		}
	}
}
