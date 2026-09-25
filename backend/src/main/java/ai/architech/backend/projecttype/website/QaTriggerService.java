package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.documentation.triggers.DocumentationTriggerEvent;
import ai.architech.backend.core.documentation.triggers.DocumentationTriggerService;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.validation.PreExecutionValidationResult;
import ai.architech.backend.core.validation.QAExecutionPreflightValidator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AIW-213's real trigger for QA execution: the missing "wire real QA execution trigger from an
 * accepted Candidate" glue - before this class, nothing in production code ever called {@link
 * QaSemanticReviewOrchestrator#run} at all (confirmed this session via {@code grep -rn
 * "qaSemanticReviewOrchestrator\.\|QaSemanticReviewOrchestrator\b" backend/src/main/java}
 * returning only the class itself and its own test). Composes the lower-level building blocks
 * this ticket also built ({@link QaExecutionInputAssembler}) with already-real, already-tested
 * components ({@link QAExecutionPreflightValidator}, {@link QaSemanticReviewOrchestrator}, {@link
 * QaResultAssemblyService}, {@link DocumentationTriggerService}) - the same "compose lower-level
 * building blocks from a higher-level caller rather than modify an already-tested class" idiom
 * {@link WebsiteGenerationDrivingService}'s own javadoc already establishes for AIW-212, and
 * {@link QaResultAssemblyService}'s own javadoc establishes for AIW-208.
 *
 * <p><b>Real, full chain for one Candidate</b>: assemble a real {@code qa-execution-input.v1}
 * payload -&gt; run it through {@link QAExecutionPreflightValidator} (fails loudly, never
 * silently proceeds on an invalid payload) -&gt; run the semantic QA Agent via {@link
 * QaSemanticReviewOrchestrator#run} -&gt; on a validated candidate, convert and persist a real
 * {@link QaResult} via {@link QaResultAssemblyService#assemble} -&gt; on a {@code PASS} gate
 * outcome, dispatch the {@code FULL_RELEASE_QA_FINALIZED} Documentation trigger.
 *
 * <p><b>Deliberate, explicit scope boundary: no QA tool-calling loop</b> - {@link
 * QaSemanticReviewOrchestrator#run} itself calls {@code BoundedRetryAgentRunner
 * .runWithRetriesUsingInputArtifacts}, which is a single {@code AgentRunner.runWithInputArtifacts}
 * call (one {@code aiGateway.invoke(request)}), not a multi-turn tool-calling loop the way {@code
 * DeveloperToolLoopOrchestrator} drives the Developer Agent. Whether a single model call is
 * actually sufficient for a genuinely grounded QA review - one that can browse/inspect the real
 * built site rather than reason from the execution-input payload alone - is a real, separate,
 * unverified concern this ticket does not resolve; {@code QaSemanticReviewOrchestrator}'s own
 * class javadoc already names it ("ready for whatever eventually calls it with a real {@code
 * AiGateway} wired to a tool-calling loop"). Building that loop is large, speculative, unticketed
 * new scope - not attempted here.
 *
 * <p><b>Best-effort, never fatal to its caller</b>: any {@link RuntimeException} from preflight,
 * the orchestrator run, result assembly, or the Documentation dispatch is caught, logged as a
 * warning, and returned as a failed {@link QaTriggerOutcome} - the same "one sibling's/one
 * candidate's own QA failure never propagates up and aborts whatever else is happening" wrapping
 * {@link WebsiteGenerationDrivingService#driveSibling} already applies to Developer execution
 * failures.
 *
 * <p>{@code targetLocale} has no real per-project resolution mechanism anywhere in this codebase
 * yet (no {@code locale} field exists on {@code customer-profile}/{@code website-requirements}) -
 * {@link #DEFAULT_TARGET_LOCALE} is this V1 epic's own frozen placeholder, the same class of
 * documented gap {@code QAExecutionPreflightValidator} already names for Preview surfaces, not a
 * silently invented convention.
 */
@Component
public class QaTriggerService {

	private static final Logger log = LoggerFactory.getLogger(QaTriggerService.class);

	static final String DEFAULT_TARGET_LOCALE = "en";

	private final QaExecutionInputAssembler qaExecutionInputAssembler;
	private final QAExecutionPreflightValidator preflightValidator;
	private final QaSemanticReviewOrchestrator orchestrator;
	private final QaResultAssemblyService qaResultAssemblyService;
	private final DocumentationTriggerService documentationTriggerService;

	QaTriggerService(
			QaExecutionInputAssembler qaExecutionInputAssembler,
			QAExecutionPreflightValidator preflightValidator,
			QaSemanticReviewOrchestrator orchestrator,
			QaResultAssemblyService qaResultAssemblyService,
			DocumentationTriggerService documentationTriggerService) {
		this.qaExecutionInputAssembler = qaExecutionInputAssembler;
		this.preflightValidator = preflightValidator;
		this.orchestrator = orchestrator;
		this.qaResultAssemblyService = qaResultAssemblyService;
		this.documentationTriggerService = documentationTriggerService;
	}

	/**
	 * Runs {@code website-qa-full-release@1.0.0} QA for {@code candidate} end to end and, on a
	 * {@code PASS} gate outcome, dispatches the {@code FULL_RELEASE_QA_FINALIZED} Documentation
	 * trigger. Never throws - every failure mode (preflight, the QA Agent run itself, or a
	 * downstream failure) is captured in the returned {@link QaTriggerOutcome} instead.
	 */
	public QaTriggerOutcome triggerFullReleaseQa(UUID projectId, WebsiteImplementationCandidate candidate) {
		try {
			String qaExecutionInputJson = qaExecutionInputAssembler.assemble(projectId, candidate);

			PreExecutionValidationResult preflight = preflightValidator.validate(projectId, qaExecutionInputJson);
			if (!preflight.valid()) {
				throw new ApplicationException(
						ErrorCode.QA_EXECUTION_INPUT_INVALID,
						"Assembled qa-execution-input for Candidate " + candidate.getId() + " failed preflight validation: "
								+ preflight.issues());
			}

			QaSemanticReviewResult result = orchestrator.run(
					projectId,
					candidate.getId(),
					QaExecutionInputAssembler.QA_PROFILE_REF,
					null,
					QaExecutionInputAssembler.TOOL_CAPABILITY_PROFILE_REF,
					qaExecutionInputJson);

			if (!result.succeeded()) {
				log.warn(
						"QA semantic review did not validate for Candidate {}: {}",
						candidate.getId(),
						result.issues());
				return QaTriggerOutcome.qaFailed(result.issues());
			}

			QaResult qaResult = qaResultAssemblyService.assemble(result.qaExecution(), result.candidateOutput().getContent());

			Optional<DocumentationTriggerService.TriggerDispatchResult> documentationDispatch = Optional.empty();
			if ("PASS".equals(qaResult.getGateOutcome())) {
				documentationDispatch = documentationTriggerService.dispatchAutomaticTrigger(
						projectId,
						candidate.getId(),
						qaResult.getId(),
						DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED,
						DEFAULT_TARGET_LOCALE,
						false);
			}

			return QaTriggerOutcome.succeeded(qaResult, documentationDispatch);
		} catch (RuntimeException e) {
			log.warn("QA trigger failed for Candidate {}: {}", candidate.getId(), e.getMessage(), e);
			return QaTriggerOutcome.infrastructureFailure(e.getMessage());
		}
	}
}
