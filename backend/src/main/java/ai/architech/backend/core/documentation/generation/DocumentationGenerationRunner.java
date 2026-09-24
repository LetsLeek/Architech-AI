package ai.architech.backend.core.documentation.generation;

import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.runner.AgentRunner;
import ai.architech.backend.core.runner.RunnerResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Steps 1-8 (config resolution, execution-context assembly, AI Gateway invocation) for the
 * Website Documentation Agent V1 (AIW-187, {@code website-documentation-agent}), bound to
 * exactly one already-frozen {@link DocumentationContext} (AIW-190) - the agent's own one
 * declared required input artifact type, {@code documentation-context}. Mirrors {@link
 * AgentRunner}'s own "steps 1-8, stop there" boundary and {@code RequirementsAnalysisRunner}'s
 * "just invoke" half of its run()/validateAndPersist() split - deliberately nothing more.
 *
 * <p><b>What this class deliberately does not do</b>: no retry (the Documentation package's own
 * {@code maxGenerationAttempts: 3} workflow-policy budget is AIW-199's orchestration concern, not
 * wired in here - this calls {@link AgentRunner#runWithInputArtifacts} directly, a single
 * un-retried attempt, and lets any {@code AgentRunnerException} propagate to the caller rather
 * than swallowing it the way {@code BoundedRetryAgentRunner} would); no output parsing or schema
 * validation of what the model returns (AIW-195's job); no run-state-machine persistence (the
 * frozen {@code documentation-run.schema.json}'s {@code PENDING → ... → CANONICALIZED} states are
 * AIW-199's own orchestration record, not built here - this class has nothing to do with a {@code
 * DocumentationRun} entity, none exists). {@link RunnerResult#candidateOutput()} is the model's
 * raw, completely unvalidated response text.
 */
@Component
public class DocumentationGenerationRunner {

	static final String AGENT_ID = "website-documentation-agent";
	static final int AGENT_VERSION = 1;
	static final String INPUT_ARTIFACT_TYPE = "documentation-context";

	private final AgentRunner agentRunner;

	DocumentationGenerationRunner(AgentRunner agentRunner) {
		this.agentRunner = agentRunner;
	}

	public RunnerResult generate(UUID projectId, DocumentationContext context) {
		Map<String, String> inputArtifacts = Map.of(INPUT_ARTIFACT_TYPE, context.getContentJson());
		return agentRunner.runWithInputArtifacts(projectId, AGENT_ID, AGENT_VERSION, inputArtifacts);
	}
}
