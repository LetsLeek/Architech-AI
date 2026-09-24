package ai.architech.backend.core.documentation.orchestration;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.documentation.context.DocumentationAuthorityAdapter;
import ai.architech.backend.core.documentation.context.DocumentationAuthoritySnapshot;
import ai.architech.backend.core.documentation.context.DocumentationContext;
import ai.architech.backend.core.documentation.context.DocumentationContextAssembler;
import ai.architech.backend.core.documentation.context.DocumentationFindingDisclosureBlockedException;
import ai.architech.backend.core.documentation.context.DocumentationSecretLeakageDetectedException;
import ai.architech.backend.core.documentation.context.DocumentationSecretScanner;
import ai.architech.backend.core.documentation.generation.DocumentationGenerationRunner;
import ai.architech.backend.core.documentation.policy.DocumentationWorkflowPolicy;
import ai.architech.backend.core.documentation.policy.DocumentationWorkflowPolicyLoader;
import ai.architech.backend.core.documentation.profiles.DocumentationProfile;
import ai.architech.backend.core.documentation.reports.DocumentationDeterministicReportGenerator;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.runner.AgentRunnerException;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.DocumentationCandidateDeterministicValidator;
import ai.architech.backend.core.validation.DocumentationCandidateStructureValidator;
import ai.architech.backend.core.validation.DocumentationCandidateValidationIssue;
import ai.architech.backend.core.validation.DocumentationCandidateValidationResult;
import ai.architech.backend.core.validation.DocumentationContextPreflightValidator;
import ai.architech.backend.core.validation.DocumentationFactualConsistencyValidator;
import ai.architech.backend.core.validation.PreExecutionValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Orchestrates the full Documentation generation pipeline (AIW-199): preflight (AIW-189) → context
 * assembly (AIW-190/191/192) → deterministic report generation (AIW-193) → a bounded generation-
 * retry loop (AIW-194 model call, then AIW-195/197/196's deterministic gate) → a bounded semantic-
 * evaluation retry loop against the one deterministically-sound candidate (AIW-198) - the same
 * "assemble config, invoke, then the deterministic validators, then the one semantic validator, all
 * bounded and typed" shape {@code BoundedRetryAgentRunner}/{@code RequirementsAnalysisRunner}
 * already establish for the Requirements Agent, adapted to Documentation's own two-tier retry
 * budget ({@code maxGenerationAttempts}/{@code maxSemanticEvaluatorExecutionsPerUnchangedCandidate},
 * {@code documentation-workflow-policy.yaml}, AIW-188).
 *
 * <p><b>Pipeline order, and why it differs from the plan's literal "Schema → Security → Profil →
 * Keys/Domain → Disclosure-Coverage → deterministische Invarianten → semantische Fidelity"
 * wording</b>: that 7-stage breakdown was written before AIW-195/196 actually existed. The real
 * components ended up bundling some of those stages together (AIW-195's one {@code validate} call
 * already does Schema+Identity+Profil-Struktur with an internal short-circuit; AIW-196's one
 * {@code validate} call already does Keys+Domains+Disclosure-Coverage+deterministic-invariants
 * together). Rather than force an artificial split that doesn't match the real code, this class
 * runs: AIW-195 (schema/identity/structure) → AIW-197 (post-generation secret scan, fail-closed,
 * never retried - {@code DOC-SEC-006}) → AIW-196 (keys/domains/disclosure/lifecycle) → AIW-198 (the
 * one costly, model-backed semantic check, run last and only against an already deterministically-
 * sound candidate).
 *
 * <p><b>Known, shared limitation - stated plainly, not silently worked around</b>: exactly like
 * {@code RequirementsAnalysisRunner}'s own "Known gap" javadoc paragraph, a regenerated attempt
 * here calls {@link DocumentationGenerationRunner#generate} fresh with the *same* {@link
 * DocumentationContext} - no validation-issue feedback is injected into the prompt. Feeding
 * correction feedback into a new attempt is future work, not built anywhere in this codebase yet
 * for any agent.
 *
 * <p><b>Retry semantics</b>: a generation attempt fails (counts against {@code
 * maxGenerationAttempts}) on an {@link AgentRunnerException} (Gateway/resolution infra failure) or
 * a deterministic validation failure (AIW-195/196). A post-generation secret leak (AIW-197) is
 * never retried - the whole run is {@link DocumentationGenerationOutcome.Blocked} immediately,
 * matching the same fail-closed framing AIW-191 already established for the pre-generation case. A
 * deterministically-sound candidate then gets up to {@code
 * maxSemanticEvaluatorExecutionsPerUnchangedCandidate} semantic-evaluation attempts against the
 * *same* candidate (never regenerating it) - but only for genuine evaluator-side failures ({@link
 * DocumentationFactualConsistencyValidator#validate} throwing, or returning {@code SYSTEM_ISSUE}-
 * class output-shape problems), per {@code FACTUAL_CONSISTENCY.md}: "same unchanged candidate at
 * most twice total; failure is {@code EVALUATION_FAILED}, NOT an Agent regeneration trigger." A
 * genuine {@code EVALUATION_ISSUE}-class semantic finding (the candidate really is unsupported) is
 * the opposite of a retry-this-same-candidate situation - it feeds back into the *outer* generation-
 * attempt budget instead, exactly like a deterministic validation failure would.
 */
@Component
public class DocumentationGenerationOrchestrator {

	private static final String EVALUATION_ISSUE_CLASS = "EVALUATION_ISSUE";

	private final DocumentationContextPreflightValidator preflightValidator;
	private final DocumentationAuthorityAdapter authorityAdapter;
	private final DocumentationContextAssembler contextAssembler;
	private final DocumentationDeterministicReportGenerator reportGenerator;
	private final DocumentationGenerationRunner generationRunner;
	private final DocumentationCandidateStructureValidator structureValidator;
	private final DocumentationSecretScanner secretScanner;
	private final DocumentationCandidateDeterministicValidator deterministicValidator;
	private final DocumentationFactualConsistencyValidator factualConsistencyValidator;
	private final DocumentationWorkflowPolicyLoader workflowPolicyLoader;
	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final QaResultRepository qaResultRepository;
	private final AgentExecutionRepository agentExecutionRepository;
	private final ObjectMapper objectMapper;

	DocumentationGenerationOrchestrator(
			DocumentationContextPreflightValidator preflightValidator,
			DocumentationAuthorityAdapter authorityAdapter,
			DocumentationContextAssembler contextAssembler,
			DocumentationDeterministicReportGenerator reportGenerator,
			DocumentationGenerationRunner generationRunner,
			DocumentationCandidateStructureValidator structureValidator,
			DocumentationSecretScanner secretScanner,
			DocumentationCandidateDeterministicValidator deterministicValidator,
			DocumentationFactualConsistencyValidator factualConsistencyValidator,
			DocumentationWorkflowPolicyLoader workflowPolicyLoader,
			WebsiteImplementationCandidateRepository candidateRepository,
			QaResultRepository qaResultRepository,
			AgentExecutionRepository agentExecutionRepository,
			ObjectMapper objectMapper) {
		this.preflightValidator = preflightValidator;
		this.authorityAdapter = authorityAdapter;
		this.contextAssembler = contextAssembler;
		this.reportGenerator = reportGenerator;
		this.generationRunner = generationRunner;
		this.structureValidator = structureValidator;
		this.secretScanner = secretScanner;
		this.deterministicValidator = deterministicValidator;
		this.factualConsistencyValidator = factualConsistencyValidator;
		this.workflowPolicyLoader = workflowPolicyLoader;
		this.candidateRepository = candidateRepository;
		this.qaResultRepository = qaResultRepository;
		this.agentExecutionRepository = agentExecutionRepository;
		this.objectMapper = objectMapper;
	}

	public DocumentationGenerationOutcome generate(
			UUID projectId, UUID candidateId, UUID qaResultId, DocumentationProfile profile, String targetLocale) {
		PreExecutionValidationResult preflight = preflightValidator.validate(projectId, candidateId, qaResultId, profile);
		if (!preflight.valid()) {
			return new DocumentationGenerationOutcome.Blocked(
					preflight.issues().stream().map(issue -> issue.path() + ": " + issue.message()).toList());
		}

		WebsiteImplementationCandidate candidate = candidateRepository.findById(candidateId).orElseThrow();
		QaResult qaResult = qaResultRepository.findById(qaResultId).orElseThrow();

		DocumentationContext context;
		try {
			context = contextAssembler.assemble(projectId, candidate, qaResult, profile, targetLocale, List.of());
		} catch (DocumentationSecretLeakageDetectedException | DocumentationFindingDisclosureBlockedException e) {
			return new DocumentationGenerationOutcome.Blocked(List.of(e.getMessage()));
		}

		DocumentationAuthoritySnapshot authoritySnapshot = authorityAdapter.buildAuthoritySnapshot(projectId, candidate, qaResult);
		List<ObjectNode> reports = reportGenerator.generate(context, candidate, qaResult, authoritySnapshot, profile);

		DocumentationWorkflowPolicy workflowPolicy = workflowPolicyLoader.load();
		int maxGenerationAttempts = workflowPolicy.maxGenerationAttempts();
		int maxEvaluationAttempts = workflowPolicy.maxSemanticEvaluatorExecutionsPerUnchangedCandidate();

		List<String> generationIssues = new ArrayList<>();
		boolean lastFailureWasInfra = false;
		int generationAttempts = 0;

		while (generationAttempts < maxGenerationAttempts) {
			generationAttempts++;

			RunnerResult runnerResult;
			try {
				runnerResult = generationRunner.generate(projectId, context);
			} catch (AgentRunnerException e) {
				lastFailureWasInfra = true;
				generationIssues.add("generation attempt " + generationAttempts + ": " + e.getMessage());
				continue;
			}
			lastFailureWasInfra = false;
			String candidateJson = runnerResult.candidateOutput();

			DocumentationCandidateValidationResult structureResult = structureValidator.validate(candidateJson, profile);
			if (!structureResult.valid()) {
				markFailed(runnerResult.execution(), describe(structureResult.issues()));
				generationIssues.addAll(prefixed(generationAttempts, structureResult.issues()));
				continue;
			}

			JsonNode candidateNode = objectMapper.readTree(candidateJson);
			try {
				secretScanner.scanCandidate(candidateNode);
			} catch (DocumentationSecretLeakageDetectedException e) {
				markFailed(runnerResult.execution(), e.getMessage());
				return new DocumentationGenerationOutcome.Blocked(List.of(e.getMessage()));
			}

			DocumentationCandidateValidationResult deterministicResult =
					deterministicValidator.validate(candidateJson, context, profile);
			if (!deterministicResult.valid()) {
				markFailed(runnerResult.execution(), describe(deterministicResult.issues()));
				generationIssues.addAll(prefixed(generationAttempts, deterministicResult.issues()));
				continue;
			}

			SemanticEvaluationOutcome semanticOutcome =
					evaluateSemanticFidelity(candidateJson, context, profile, maxEvaluationAttempts);

			if (semanticOutcome instanceof SemanticEvaluationOutcome.Passed passed) {
				runnerResult.execution().succeed();
				agentExecutionRepository.save(runnerResult.execution());
				return new DocumentationGenerationOutcome.Success(
						context, candidateJson, reports, generationAttempts, passed.attempts());
			}
			if (semanticOutcome instanceof SemanticEvaluationOutcome.Exhausted exhausted) {
				markFailed(runnerResult.execution(), "semantic evaluator exhausted its own retry budget");
				return new DocumentationGenerationOutcome.EvaluationFailed(
						exhausted.infraIssues(), generationAttempts, exhausted.attempts());
			}

			SemanticEvaluationOutcome.RealFinding realFinding = (SemanticEvaluationOutcome.RealFinding) semanticOutcome;
			markFailed(runnerResult.execution(), "semantic evaluator found unsupported claims");
			generationIssues.addAll(describeFindings(generationAttempts, realFinding.findings()));
			lastFailureWasInfra = false;
		}

		return lastFailureWasInfra
				? new DocumentationGenerationOutcome.GenerationFailed(generationIssues, generationAttempts)
				: new DocumentationGenerationOutcome.ValidationFailed(generationIssues, generationAttempts);
	}

	/**
	 * The bounded, same-candidate semantic-evaluation retry loop (step 5) - never regenerates a
	 * candidate itself, only ever re-runs {@link DocumentationFactualConsistencyValidator} against
	 * the exact same {@code candidateJson}.
	 */
	private SemanticEvaluationOutcome evaluateSemanticFidelity(
			String candidateJson, DocumentationContext context, DocumentationProfile profile, int maxAttempts) {
		List<String> infraIssues = new ArrayList<>();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			List<JsonNode> issues;
			try {
				issues = factualConsistencyValidator.validate(candidateJson, context, profile);
			} catch (RuntimeException e) {
				infraIssues.add("evaluation attempt " + attempt + ": " + e.getMessage());
				continue;
			}

			List<JsonNode> realFindings =
					issues.stream().filter(issue -> EVALUATION_ISSUE_CLASS.equals(issue.path("issueClass").asString(null))).toList();
			if (!realFindings.isEmpty()) {
				return new SemanticEvaluationOutcome.RealFinding(realFindings, attempt);
			}

			List<JsonNode> infraShapedIssues =
					issues.stream().filter(issue -> !EVALUATION_ISSUE_CLASS.equals(issue.path("issueClass").asString(null))).toList();
			if (!infraShapedIssues.isEmpty()) {
				infraIssues.add("evaluation attempt " + attempt + ": " + infraShapedIssues.size() + " validator-output issue(s)");
				continue;
			}

			return new SemanticEvaluationOutcome.Passed(attempt);
		}
		return new SemanticEvaluationOutcome.Exhausted(infraIssues, maxAttempts);
	}

	private sealed interface SemanticEvaluationOutcome {
		record Passed(int attempts) implements SemanticEvaluationOutcome {}

		record RealFinding(List<JsonNode> findings, int attempts) implements SemanticEvaluationOutcome {}

		record Exhausted(List<String> infraIssues, int attempts) implements SemanticEvaluationOutcome {}
	}

	private void markFailed(AgentExecution execution, String reason) {
		execution.fail(reason);
		agentExecutionRepository.save(execution);
	}

	private String describe(List<DocumentationCandidateValidationIssue> issues) {
		return issues.stream().map(issue -> "[" + issue.validator() + "] " + issue.reason()).reduce((a, b) -> a + "; " + b).orElse("");
	}

	private List<String> prefixed(int attempt, List<DocumentationCandidateValidationIssue> issues) {
		return issues.stream()
				.map(issue -> "generation attempt " + attempt + " [" + issue.validator() + "] " + issue.ref() + ": " + issue.reason())
				.toList();
	}

	private List<String> describeFindings(int attempt, List<JsonNode> findings) {
		return findings.stream()
				.map(issue -> "generation attempt " + attempt + " [" + issue.path("issueCode").asString("?") + "] "
						+ issue.path("candidateClaimKey").asString("?") + ": " + issue.path("safeMessage").asString("?"))
				.toList();
	}
}
