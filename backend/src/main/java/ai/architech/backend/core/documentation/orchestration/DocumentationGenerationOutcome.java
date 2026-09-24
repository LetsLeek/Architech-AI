package ai.architech.backend.core.documentation.orchestration;

import ai.architech.backend.core.documentation.context.DocumentationContext;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * The typed outcome of one {@link DocumentationGenerationOrchestrator#generate} call (AIW-199) -
 * an in-memory mirror of the meaningful subset of {@code documentation-run.schema.json}'s own
 * {@code runState} enum ({@code PENDING, CONTEXT_BUILDING, CONTEXT_READY, GENERATING, VALIDATING,
 * CANONICALIZED, BLOCKED, GENERATION_FAILED, VALIDATION_FAILED, EVALUATION_FAILED, SYSTEM_FAILED,
 * CANCELLED}), never persisted as a {@code DocumentationRun} row.
 *
 * <p><b>Deliberately not a JPA entity, deliberately not persisted anywhere</b>: there is no real
 * caller yet (no controller endpoint, no workflow trigger - AIW-202 doesn't exist), and real
 * canonical persistence with linear revisioning is explicitly AIW-200's own separate scope (a
 * {@code DocumentationPackageVersion}, a fundamentally different concept than a run-audit-trail).
 * Persisting a {@code DocumentationRun} row prematurely, before anything real consumes it, risks a
 * schema/design mismatch once a real caller and AIW-200's persistence actually exist - the same
 * "build the classifier before its real input source exists" boundary {@code
 * CandidateBindingValidator}/{@code QAPolicyAggregator} already establish elsewhere in this
 * codebase for their own not-yet-wired concerns.
 */
public sealed interface DocumentationGenerationOutcome {

	/**
	 * Everything a future AIW-200 persistence step or AIW-201 renderer would need.
	 *
	 * <p>{@code originAgentExecutionId} substitutes for {@code documentation-package-version.schema
	 * .json}'s schema-required {@code originRunRef}: there is no persisted {@code DocumentationRun}
	 * row to point at (this class's own javadoc explains why), so AIW-200 points at the real {@code
	 * AgentExecution} that actually produced the winning candidate instead - the closest real,
	 * already-persisted row to "the run that produced this."
	 */
	record Success(
			DocumentationContext context,
			String candidateJson,
			List<ObjectNode> reports,
			int generationAttemptCount,
			int evaluationAttemptCount,
			UUID originAgentExecutionId)
			implements DocumentationGenerationOutcome {}

	/**
	 * Preflight rejected the request, or context assembly fail-closed (a genuine secret leak or a
	 * blocking/escalated/unmapped customer finding) - never retried, matching PREFLIGHT.md's own
	 * "typed safe preflight issue, {@code BLOCKED}" framing and {@code DOC-SEC-006}'s "fail closed
	 * and investigate."
	 */
	record Blocked(List<String> reasons) implements DocumentationGenerationOutcome {}

	/** Every generation attempt failed at the AI Gateway/infra level (never produced a candidate to validate). */
	record GenerationFailed(List<String> reasons, int generationAttemptCount) implements DocumentationGenerationOutcome {}

	/** Every generation attempt produced a candidate, but none ever passed AIW-195/197/196's deterministic gate. */
	record ValidationFailed(List<String> reasons, int generationAttemptCount) implements DocumentationGenerationOutcome {}

	/**
	 * The semantic evaluator's own bounded retry budget ({@code
	 * maxSemanticEvaluatorExecutionsPerUnchangedCandidate}) was exhausted on evaluator-side
	 * failures alone (never produced a real answer) - per {@code FACTUAL_CONSISTENCY.md}: "failure
	 * is {@code EVALUATION_FAILED}, NOT an Agent regeneration trigger."
	 */
	record EvaluationFailed(List<String> reasons, int generationAttemptCount, int evaluationAttemptCount)
			implements DocumentationGenerationOutcome {}
}
