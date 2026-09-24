package ai.architech.backend.core.runner;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link AgentRunner} with a platform-owned, bounded retry budget - the agent itself
 * never decides whether or how often to retry. Each attempt calls {@link AgentRunner#run},
 * which always creates a fresh {@code AgentExecution}, so a retry is never a mutation of a
 * previous attempt's record. Retries reuse the exact evidence snapshot passed in; starting a
 * new cycle against different evidence means calling this with a different snapshot id, a
 * decision left entirely to the caller - nothing here takes or refreshes a snapshot itself.
 *
 * <p>The only failure signal available right now is {@link AgentRunnerException} (agent/
 * skill/rule resolution or Gateway failures). Once the Validation Pipeline exists
 * (AIW-42, 47..49), a validation failure will need to feed into this same bounded loop
 * rather than becoming a second, separate retry mechanism.
 */
@Component
public class BoundedRetryAgentRunner {

	private final AgentRunner agentRunner;
	private final RunnerProperties properties;

	BoundedRetryAgentRunner(AgentRunner agentRunner, RunnerProperties properties) {
		this.agentRunner = agentRunner;
		this.properties = properties;
	}

	public RunnerResult runWithRetries(UUID evidenceSnapshotId, String agentId, int agentVersion) {
		AgentRunnerException lastFailure = null;

		for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
			try {
				return agentRunner.run(evidenceSnapshotId, agentId, agentVersion);
			} catch (AgentRunnerException e) {
				lastFailure = e;
			}
		}

		throw new RetryBudgetExhaustedException(agentId, properties.maxAttempts(), lastFailure);
	}

	/** Same bounded-retry wrapping as {@link #runWithRetries}, for {@link AgentRunner#runWithInputArtifacts}. */
	public RunnerResult runWithRetriesUsingInputArtifacts(
			UUID projectId, String agentId, int agentVersion, Map<String, String> inputArtifactsByType) {
		AgentRunnerException lastFailure = null;

		for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
			try {
				return agentRunner.runWithInputArtifacts(projectId, agentId, agentVersion, inputArtifactsByType);
			} catch (AgentRunnerException e) {
				lastFailure = e;
			}
		}

		throw new RetryBudgetExhaustedException(agentId, properties.maxAttempts(), lastFailure);
	}
}
