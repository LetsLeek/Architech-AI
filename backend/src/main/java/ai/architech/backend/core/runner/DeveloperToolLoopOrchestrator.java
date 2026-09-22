package ai.architech.backend.core.runner;

import ai.architech.backend.core.agent.AgentDefinition;
import ai.architech.backend.core.agent.AgentDefinitionLoader;
import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.ai.AiGateway;
import ai.architech.backend.core.ai.AiMessage;
import ai.architech.backend.core.ai.AiRequest;
import ai.architech.backend.core.ai.AiResponse;
import ai.architech.backend.core.ai.AiUsageBudgetGuard;
import ai.architech.backend.core.ai.ContentBlock;
import ai.architech.backend.core.ai.CostCalculator;
import ai.architech.backend.core.ai.ToolSchema;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateAcceptanceCoordinator;
import ai.architech.backend.core.developer.tooling.DeveloperToolCapabilityProfile;
import ai.architech.backend.core.developer.tooling.DeveloperToolCapabilityProfileLoader;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.rule.RuleDefinition;
import ai.architech.backend.core.rule.RuleLoader;
import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.core.skill.SkillDefinition;
import ai.architech.backend.core.skill.SkillLoader;
import ai.architech.backend.core.toolexecution.ToolExecution;
import ai.architech.backend.core.toolexecution.ToolExecutionRepository;
import ai.architech.backend.core.validation.DeveloperResultValidationPersister;
import ai.architech.backend.core.validation.DeveloperResultValidationResult;
import ai.architech.backend.core.validation.DeveloperResultValidator;
import ai.architech.backend.core.validation.OutputContractParser;
import ai.architech.backend.core.validation.OutputContractResult;
import ai.architech.backend.core.verification.AuthoritativeRunnerVerifier;
import ai.architech.backend.core.verification.AuthorizedExternalTarget;
import ai.architech.backend.core.verification.RunnerVerificationEvidencePersister;
import ai.architech.backend.core.verification.RunnerVerificationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Developer Agent's own multi-turn tool-calling loop (AIW-184, "Track 2C") - the one piece
 * that was missing from an otherwise fully-built M3 pipeline: turns an assembled {@code
 * developer-execution-input.v1} payload into a real, sandbox-produced {@code
 * developer-agent-result:v1} by actually dispatching the model's requested tool calls, rather
 * than a single one-shot {@link AiGateway} call the way {@link AgentRunner} makes for M1/M2.
 * Deliberately a new class, never grafted onto {@link AgentRunner} itself - {@link
 * AgentRunner#buildMessagesFromInputArtifacts} is reused as-is for the initial system/user turn,
 * since role/rules/skills/output-schema assembly is identical; everything after that (the
 * multi-turn loop, tool dispatch, correction-cycle wiring, and handoff to the already-built
 * validation/verification/acceptance pipeline) is new.
 *
 * <p>One call to {@link #run} drives exactly one {@link AgentExecution} from {@code PENDING} to
 * a terminal status: {@code SUCCEEDED} (with an accepted {@link WebsiteImplementationCandidate}),
 * {@code BLOCKED} (a genuine, valid completion blocker - no Candidate), {@code FAILED} (a
 * Developer-owned defect that exhausted its correction budget - no Candidate), or {@code ERROR}
 * (an infrastructure malfunction - no Candidate, no speculative source changes). Every requested
 * tool call is checked against the execution's own {@link DeveloperToolCapabilityProfile}
 * allowlist before ever touching the sandbox ({@link DeveloperToolDispatcher}), and persisted as
 * exactly one {@link ToolExecution} row regardless of outcome.
 *
 * <p>{@link AiUsageBudgetGuard#checkBeforeInvoking} is called once per execution, before the
 * first model call - the same per-attempt (not per-turn) granularity {@link AgentRunner} already
 * uses - rather than once per turn, since a single execution attempting many turns is still one
 * budget-relevant "attempt" from the caller's perspective; token/cost usage across every turn is
 * still summed and recorded on the execution when it finishes, so nothing is under-reported.
 */
@Component
public class DeveloperToolLoopOrchestrator {

	static final String AGENT_ID = "developer-agent";
	static final int AGENT_VERSION = 1;
	private static final String INPUT_ARTIFACT_TYPE = "developer-execution-input";
	private static final String OUTPUT_ARTIFACT_TYPE = "developer-agent-result";
	private static final int REFERENCED_DEFINITION_VERSION = 1;

	private final AgentDefinitionLoader agentDefinitionLoader;
	private final SkillLoader skillLoader;
	private final RuleLoader ruleLoader;
	private final AiGateway aiGateway;
	private final CostCalculator costCalculator;
	private final AiUsageBudgetGuard aiUsageBudgetGuard;
	private final AgentExecutionRepository agentExecutionRepository;
	private final DeveloperToolCapabilityProfileLoader toolCapabilityProfileLoader;
	private final DeveloperToolDispatcher toolDispatcher = new DeveloperToolDispatcher();
	private final ToolExecutionRepository toolExecutionRepository;
	private final OutputContractParser outputContractParser;
	private final DeveloperResultValidator developerResultValidator;
	private final DeveloperResultValidationPersister developerResultValidationPersister;
	private final HandoffFreezeGate handoffFreezeGate;
	private final AuthoritativeRunnerVerifier authoritativeRunnerVerifier;
	private final RunnerVerificationEvidencePersister runnerVerificationEvidencePersister;
	private final WebsiteImplementationCandidateAcceptanceCoordinator candidateAcceptanceCoordinator;
	private final DeveloperToolLoopProperties properties;
	private final ObjectMapper objectMapper;

	DeveloperToolLoopOrchestrator(
			AgentDefinitionLoader agentDefinitionLoader,
			SkillLoader skillLoader,
			RuleLoader ruleLoader,
			AiGateway aiGateway,
			CostCalculator costCalculator,
			AiUsageBudgetGuard aiUsageBudgetGuard,
			AgentExecutionRepository agentExecutionRepository,
			DeveloperToolCapabilityProfileLoader toolCapabilityProfileLoader,
			ToolExecutionRepository toolExecutionRepository,
			OutputContractParser outputContractParser,
			DeveloperResultValidator developerResultValidator,
			DeveloperResultValidationPersister developerResultValidationPersister,
			HandoffFreezeGate handoffFreezeGate,
			AuthoritativeRunnerVerifier authoritativeRunnerVerifier,
			RunnerVerificationEvidencePersister runnerVerificationEvidencePersister,
			WebsiteImplementationCandidateAcceptanceCoordinator candidateAcceptanceCoordinator,
			DeveloperToolLoopProperties properties,
			ObjectMapper objectMapper) {
		this.agentDefinitionLoader = agentDefinitionLoader;
		this.skillLoader = skillLoader;
		this.ruleLoader = ruleLoader;
		this.aiGateway = aiGateway;
		this.costCalculator = costCalculator;
		this.aiUsageBudgetGuard = aiUsageBudgetGuard;
		this.agentExecutionRepository = agentExecutionRepository;
		this.toolCapabilityProfileLoader = toolCapabilityProfileLoader;
		this.toolExecutionRepository = toolExecutionRepository;
		this.outputContractParser = outputContractParser;
		this.developerResultValidator = developerResultValidator;
		this.developerResultValidationPersister = developerResultValidationPersister;
		this.handoffFreezeGate = handoffFreezeGate;
		this.authoritativeRunnerVerifier = authoritativeRunnerVerifier;
		this.runnerVerificationEvidencePersister = runnerVerificationEvidencePersister;
		this.candidateAcceptanceCoordinator = candidateAcceptanceCoordinator;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	/**
	 * {@code workspace} must already be an isolated, git-tracked, unfrozen checkout at the exact
	 * {@code developmentBaseRef} the caller's own {@code executionInputJson} names - provisioning
	 * it (via {@code DevelopmentBaseProvisioner}) is the caller's concern, same division of
	 * responsibility {@link AgentRunner#runWithInputArtifacts}'s own javadoc already establishes
	 * for keeping Website-specific concerns out of the generic runner layer.
	 */
	public DeveloperToolLoopResult run(UUID projectId, String executionInputJson, Workspace workspace) {
		JsonNode input = objectMapper.readTree(executionInputJson);
		String toolCapabilityProfileRef = input.path("technicalContext").path("toolCapabilityProfileRef").asString(null);
		int maxCorrectionCycles = input.path("executionContext").path("correctionBudget").path("maxCorrectionCycles").asInt(0);
		DeveloperToolCapabilityProfile profile = toolCapabilityProfileLoader.resolve(toolCapabilityProfileRef);

		AgentExecution execution = new AgentExecution(projectId, AGENT_ID, AGENT_VERSION);
		agentExecutionRepository.saveAndFlush(execution);

		// Deliberately outside the loop's own try/catch below - a budget breach is a definitive
		// refusal to even attempt this execution, matching AgentRunner's own identical posture.
		try {
			aiUsageBudgetGuard.checkBeforeInvoking(projectId, AGENT_ID);
		} catch (ApplicationException e) {
			execution.fail(e.getMessage());
			agentExecutionRepository.saveAndFlush(execution);
			throw e;
		}

		execution.start();
		agentExecutionRepository.saveAndFlush(execution);

		AgentDefinition agentDefinition = agentDefinitionLoader.resolve(AGENT_ID, AGENT_VERSION);
		List<SkillDefinition> skills =
				agentDefinition.skills().stream().map(skillId -> skillLoader.resolve(skillId, REFERENCED_DEFINITION_VERSION)).toList();
		List<RuleDefinition> rules =
				agentDefinition.rules().stream().map(ruleId -> ruleLoader.resolve(ruleId, REFERENCED_DEFINITION_VERSION)).toList();
		List<AiMessage> conversation = new ArrayList<>(AgentRunner.buildMessagesFromInputArtifacts(
				agentDefinition, skills, rules, Map.of(INPUT_ARTIFACT_TYPE, executionInputJson)));
		List<ToolSchema> tools = DeveloperToolSchemas.all(objectMapper);
		String correlationId = execution.getId().toString();

		LoopState state = new LoopState();

		try {
			while (true) {
				if (state.turns >= properties.maxTurns()) {
					return terminateBudgetExhausted(execution, state, "tool-calling turn budget (" + properties.maxTurns() + ") exhausted");
				}
				state.turns++;

				AiRequest request =
						new AiRequest(agentDefinition.modelProfile(), List.copyOf(conversation), agentDefinition.limits().maxOutputTokens(), correlationId, tools);
				AiResponse response = aiGateway.invoke(request);
				state.recordUsage(response, costCalculator);

				if (response.requiresToolUse()) {
					if (!dispatchToolTurn(execution, state, profile, workspace, response, conversation)) {
						return terminateBudgetExhausted(
								execution, state, "tool-call budget (" + properties.maxToolCalls() + ") exhausted");
					}
					continue;
				}

				// WorkspaceFileSystem itself has no git awareness at all (it only ever touches the
				// filesystem) - without staging as soon as the model stops requesting tools,
				// nothing the loop wrote via the filesystem tool would be visible to
				// repositoryFilesOf/HandoffFreezeGate's own git-ls-files-based views, making
				// validation/verification evaluate stale (pre-write) state.
				stageAllChanges(workspace);

				OutputContractResult contract = outputContractParser.parse(response.content(), Set.of(OUTPUT_ARTIFACT_TYPE));
				if (!contract.valid()) {
					if (!authorizeCorrection(execution, workspace, maxCorrectionCycles, "output-contract validation failed: " + contract.issues())) {
						return terminate(execution, state, null);
					}
					conversation.add(new AiMessage(
							"user",
							"Your previous response failed output-contract validation: " + contract.issues()
									+ ". Correct it and respond again with exactly one JSON object as instructed."));
					continue;
				}

				String developerResultJson = contract.artifactContentByType().get(OUTPUT_ARTIFACT_TYPE);
				List<ToolExecution> toolExecutions = toolExecutionRepository.findByAgentExecutionIdOrderByStartedAtAsc(execution.getId());
				DeveloperResultValidationResult validation = developerResultValidator.validate(
						developerResultJson, executionInputJson, projectId, repositoryFilesOf(workspace), toolExecutions);
				developerResultValidationPersister.persist(execution.getId(), validation);

				if (!validation.valid()) {
					if (!authorizeCorrection(execution, workspace, maxCorrectionCycles, "result validation failed: " + validation.issues())) {
						return terminate(execution, state, null);
					}
					conversation.add(new AiMessage(
							"user",
							"Your previous result failed validation: " + validation.issues()
									+ ". Correct it using the tools available and respond again."));
					continue;
				}

				String resultType = objectMapper.readTree(developerResultJson).path("resultType").asString(null);
				if ("BLOCKED".equals(resultType)) {
					execution.block(blockerSummaryOf(developerResultJson));
					return terminate(execution, state, null);
				}

				// Already staged above (right after this turn was recognized as a final answer,
				// before validation ran against repositoryFilesOf) - freezing now sees the exact
				// same tracked state validation just evaluated.
				FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
				RunnerVerificationResult verificationResult =
						authoritativeRunnerVerifier.verify(workspace, snapshot, List.<AuthorizedExternalTarget>of());
				runnerVerificationEvidencePersister.persist(execution.getId(), snapshot.snapshotId(), verificationResult);

				if (!verificationResult.passed()) {
					if (!authorizeCorrection(
							execution, workspace, maxCorrectionCycles, "authoritative Runner verification failed: " + verificationResult.outcome())) {
						return terminate(execution, state, null);
					}
					conversation.add(new AiMessage(
							"user",
							"Authoritative Runner verification failed (" + verificationResult.outcome()
									+ "). Correct it using the tools available and respond again."));
					continue;
				}

				state.applyTo(execution, costCalculator);
				agentExecutionRepository.saveAndFlush(execution);
				WebsiteImplementationCandidate candidate = candidateAcceptanceCoordinator.accept(
						execution, projectId, developerResultJson, executionInputJson, workspace, snapshot);
				return new DeveloperToolLoopResult(execution, candidate);
			}
		} catch (RuntimeException e) {
			execution.error(e.getMessage());
			state.applyTo(execution, costCalculator);
			agentExecutionRepository.saveAndFlush(execution);
			throw new AgentRunnerException("Developer tool-calling loop for execution " + execution.getId() + " failed", e);
		}
	}

	/** Returns {@code false} once the shared tool-call budget is exhausted mid-turn - the caller must terminate immediately in that case, without dispatching the remaining requested calls. */
	private boolean dispatchToolTurn(
			AgentExecution execution,
			LoopState state,
			DeveloperToolCapabilityProfile profile,
			Workspace workspace,
			AiResponse response,
			List<AiMessage> conversation) {
		conversation.add(new AiMessage("assistant", response.blocks()));
		List<ContentBlock> resultBlocks = new ArrayList<>();
		for (ContentBlock.ToolUse toolUse : response.toolUses()) {
			if (state.toolCallsUsed >= properties.maxToolCalls()) {
				return false;
			}
			state.toolCallsUsed++;

			DeveloperToolCallOutcome outcome = toolDispatcher.dispatch(profile, workspace, toolUse.name(), toolUse.input());
			Instant now = Instant.now();
			toolExecutionRepository.saveAndFlush(new ToolExecution(
					execution.getId(), outcome.capability(), toolUse.name(), state.correctionCycle, outcome.status(), outcome.detail(), now, now));
			resultBlocks.add(new ContentBlock.ToolResult(toolUse.id(), outcome.detail(), outcome.isError()));
		}
		conversation.add(new AiMessage("user", resultBlocks));
		return true;
	}

	/** {@code false} means the execution is already terminal (FAILED) - the caller must stop, never loop again. */
	private boolean authorizeCorrection(AgentExecution execution, Workspace workspace, int maxCorrectionCycles, String failureReason) {
		boolean authorized = execution.authorizeCorrectionCycleOrFail(maxCorrectionCycles, failureReason);
		agentExecutionRepository.saveAndFlush(execution);
		if (authorized) {
			handoffFreezeGate.unfreezeForCorrection(workspace);
		}
		return authorized;
	}

	private DeveloperToolLoopResult terminateBudgetExhausted(AgentExecution execution, LoopState state, String reason) {
		execution.fail(reason);
		return terminate(execution, state, null);
	}

	private DeveloperToolLoopResult terminate(AgentExecution execution, LoopState state, WebsiteImplementationCandidate candidate) {
		state.applyTo(execution, costCalculator);
		agentExecutionRepository.saveAndFlush(execution);
		return new DeveloperToolLoopResult(execution, candidate);
	}

	private static String blockerSummaryOf(String developerResultJson) {
		JsonNode blockers = new ObjectMapper().readTree(developerResultJson).path("blockers");
		if (blockers.isArray() && !blockers.isEmpty()) {
			return blockers.get(0).path("diagnosticSummary").asString("blocked");
		}
		return "blocked";
	}

	/** Stages every change (new/modified/deleted tracked-candidate files) so {@link HandoffFreezeGate} and {@link #repositoryFilesOf} both see what the loop actually wrote. */
	private static void stageAllChanges(Workspace workspace) {
		try {
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			// Fixed literal git subcommand, never caller-supplied text.
			Process process = new ProcessBuilder("git", "add", "-A").directory(workspace.root().toFile()).start();
			process.getInputStream().readAllBytes();
			process.getErrorStream().readAllBytes();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new IllegalStateException("git add -A exited " + exitCode + " in " + workspace.root());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to stage changes in " + workspace.root(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while staging changes in " + workspace.root(), e);
		}
	}

	/** {@code git ls-files} against the workspace root - the same tracked-file universe {@link HandoffFreezeGate} itself hashes, needed here so {@link DeveloperResultValidator}'s implementation-anchor check can confirm an anchor targets a path that genuinely exists in the frozen repository state. */
	private static Set<String> repositoryFilesOf(Workspace workspace) {
		Process process;
		try {
			// nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
			// Fixed literal git subcommand, never caller-supplied text - same justification as
			// HandoffFreezeGate/SecretScanGate/core.sandbox.ProcessRunner.
			process = new ProcessBuilder("git", "ls-files").directory(workspace.root().toFile()).start();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to list tracked files in " + workspace.root(), e);
		}
		try {
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			process.getErrorStream().readAllBytes();
			process.waitFor();
			return Set.copyOf(output.lines().filter(line -> !line.isBlank()).toList());
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("Failed to read tracked file list for " + workspace.root(), e);
		}
	}

	/** Accumulates token/cost usage across every turn of one execution, applied to the {@link AgentExecution} row exactly once, right before it reaches a terminal status. */
	private static final class LoopState {
		private int turns;
		private int toolCallsUsed;
		private int correctionCycle;
		private String provider;
		private String model;
		private int promptTokens;
		private int completionTokens;
		private BigDecimal cost = BigDecimal.ZERO;

		void recordUsage(AiResponse response, CostCalculator costCalculator) {
			provider = response.provider();
			model = response.model();
			promptTokens += nz(response.promptTokens());
			completionTokens += nz(response.completionTokens());
			// CostCalculator returns null (never a fabricated zero) when it has no pricing for this
			// provider/model pair - once any turn's own cost is unknown, the whole execution's
			// accumulated cost stays unknown rather than silently under-reporting it as the sum of
			// only the turns that happened to have pricing.
			if (cost != null) {
				BigDecimal turnCost = costCalculator.calculateUsd(
						response.provider(),
						response.model(),
						response.promptTokens(),
						response.completionTokens(),
						response.cacheCreationInputTokens(),
						response.cacheReadInputTokens());
				cost = turnCost == null ? null : cost.add(turnCost);
			}
		}

		void applyTo(AgentExecution execution, CostCalculator costCalculator) {
			if (provider != null) {
				execution.recordModelUsage(provider, model, promptTokens, completionTokens, cost);
			}
		}

		private static int nz(Integer value) {
			return value == null ? 0 : value;
		}
	}
}
