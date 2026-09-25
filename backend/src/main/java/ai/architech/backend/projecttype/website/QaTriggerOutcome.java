package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.documentation.triggers.DocumentationTriggerService;
import ai.architech.backend.core.qa.QaResult;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of one {@link QaTriggerService#triggerFullReleaseQa} call. Exactly one of {@code
 * qaResult} or {@code failureMessage} is non-{@code null}/non-empty:
 *
 * <ul>
 *   <li>{@link #succeeded} - the QA Agent's own output validated and a real {@link QaResult} was
 *       assembled and persisted; {@code documentationDispatch} is present only when that result's
 *       gate outcome was {@code PASS} <em>and</em> {@link
 *       ai.architech.backend.core.documentation.triggers.DocumentationWorkflowTriggerEvaluator}
 *       itself judged the Candidate release-eligible.
 *   <li>{@link #qaFailed} - the QA Agent ran, but its output failed output-contract/schema/
 *       identity validation; {@code qaIssues} carries the same issue strings {@link
 *       QaSemanticReviewResult#issues()} produced. No {@link QaResult} exists for this attempt -
 *       {@link QaResultAssemblyService} is deliberately never called on an invalid candidate.
 *   <li>{@link #infrastructureFailure} - assembly, preflight, the orchestrator call itself, result
 *       assembly, or the Documentation dispatch threw. This Candidate's QA never even produced a
 *       validated (or invalid) semantic review output.
 * </ul>
 */
public record QaTriggerOutcome(
		QaResult qaResult,
		Optional<DocumentationTriggerService.TriggerDispatchResult> documentationDispatch,
		List<String> qaIssues,
		String failureMessage) {

	public static QaTriggerOutcome succeeded(
			QaResult qaResult, Optional<DocumentationTriggerService.TriggerDispatchResult> documentationDispatch) {
		return new QaTriggerOutcome(qaResult, documentationDispatch, List.of(), null);
	}

	public static QaTriggerOutcome qaFailed(List<String> qaIssues) {
		return new QaTriggerOutcome(null, Optional.empty(), qaIssues, null);
	}

	public static QaTriggerOutcome infrastructureFailure(String failureMessage) {
		return new QaTriggerOutcome(null, Optional.empty(), List.of(), failureMessage);
	}

	public boolean succeeded() {
		return qaResult != null;
	}
}
