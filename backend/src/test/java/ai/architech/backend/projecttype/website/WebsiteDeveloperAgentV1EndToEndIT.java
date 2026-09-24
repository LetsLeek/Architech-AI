package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.candidate.RepositoryStateMismatchException;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateAcceptanceCoordinator;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.integration.IntegrationContract;
import ai.architech.backend.core.integration.IntegrationContractRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.sandbox.Workspace;
import ai.architech.backend.core.validation.DeveloperResultValidationResult;
import ai.architech.backend.core.validation.DeveloperResultValidator;
import ai.architech.backend.core.verification.AuthoritativeRunnerVerifier;
import ai.architech.backend.core.verification.GateResult;
import ai.architech.backend.core.verification.RunnerVerificationEvidencePersister;
import ai.architech.backend.core.verification.RunnerVerificationResult;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.VerificationOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Website Developer Agent V1 end-to-end fixtures and tests (AIW-147) - chains the real pieces
 * built across M3 (AIW-131-161) into one connected flow per named scenario: {@link
 * DeveloperExecutionInputAssembler} -&gt; real git workspace -&gt; {@link DeveloperResultValidator}
 * -&gt; {@link RunnerVerificationEvidencePersister} -&gt; {@link
 * WebsiteImplementationCandidateAcceptanceCoordinator}. No live model call anywhere, and no
 * dependency on AIW-184's still-unbuilt tool-calling loop - exactly {@code
 * InitialGenerationOrchestratorIT}'s own established posture (drive {@link AgentExecution}
 * transitions and hand-typed JSON standing in for "what the agent would eventually produce"
 * directly into the real downstream pipeline).
 *
 * <p>Every scenario named in this ticket's own AC already has dedicated, real-git/real-DB proof
 * somewhere in this suite that this class deliberately does not re-test:
 *
 * <ul>
 *   <li>Invalid/fake design/requirement/integration refs fail result validation - {@code
 *       DeveloperResultReferenceValidatorTests}, {@code
 *       DeveloperResultTargetIdentityValidatorTests}, {@code
 *       DeveloperResultValidatorIT#rejectsATargetDesignMismatch}.
 *   <li>Missing Page/Section anchors fail result validation - {@code
 *       ImplementationAnchorValidatorTests}, {@code
 *       DeveloperResultValidatorIT#rejectsMissingPageCoverageAnchors}.
 *   <li>Candidate persistence is immutable/idempotent and references exactly the verified
 *       repository state - {@code WebsiteImplementationCandidateAcceptanceCoordinatorIT} (crash-
 *       boundary/idempotency proofs), {@code WebsiteImplementationCandidatePromoterIT}.
 *   <li>Initial A/B/C workflow produces three isolated equal-policy Candidates or explicit
 *       escalation - {@code InitialGenerationOrchestratorIT} (fan-out, isolation, bounded
 *       retry/escalation over three sibling executions).
 *   <li>A genuine blocker produces {@code BLOCKED} and no Candidate - {@code
 *       DeveloperResultValidatorIT#acceptsAValidBlockedHandoffWithSupportingEvidence}, {@code
 *       DeveloperExecutionAuditTrailAssemblerIT#aSemanticallyBlockedExecutionIsFullyTraceableWithNoCandidate}.
 * </ul>
 *
 * <p>This class adds the connected chain those pieces never get run through together, plus the
 * scenarios genuinely uncovered anywhere else: {@code IMPLEMENTED_BOUND} against a real,
 * DB-seeded Integration Contract; a schema-valid {@code UNBOUND} binding that is still
 * Candidate-eligible; an explicit, non-fabricated {@code MISSING_UPSTREAM_INFORMATION} gap; a
 * bounded-correction-budget exhaustion chained with real verification evidence ending {@code
 * FAILED}; a genuine sandbox/infrastructure {@code ERROR} that leaves the workspace untouched;
 * and a secret-scan verification failure that never reaches an accepted Candidate.
 */
@SpringBootTest
@Transactional
class WebsiteDeveloperAgentV1EndToEndIT {

	private static final String CUSTOMER_PROFILE =
			"""
			{
			  "business": {"name": "Green Leaf Cafe"},
			  "contact": {"phone": "+43 1 2345678"},
			  "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}],
			  "offerings": [{"localRef": "cust-off-1", "name": "Coffee"}],
			  "openingHours": [], "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": []
			}
			""";

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{
			  "goals": [], "targetAudiences": [], "contentRequirements": [],
			  "functionalRequirements": [
			    {"localRef": "req-func-1", "type": "booking", "description": "Let customers book a table", "strength": "must", "sourceRefs": ["s1"]}
			  ],
			  "languages": [], "constraints": [], "unknowns": [], "conflicts": []
			}
			""";

	private static final String PROPOSAL_SET_JSON =
			"""
			{"proposals": [%s]}
			"""
					.formatted(
							"""
							{
							  "localRef": "prop-a",
							  "name": "Warm Minimal",
							  "concept": "A calm, minimal layout emphasizing the menu.",
							  "websitePlan": {
							    "requirementRefs": ["req-func-1"],
							    "pages": [
							      {
							        "localRef": "page-a-home", "name": "Home", "route": "/",
							        "purpose": "Introduce the cafe and lead to booking",
							        "sections": [
							          {
							            "localRef": "sec-a-hero", "kind": "hero", "purpose": "Welcome visitors",
							            "layoutIntent": "centered, single column",
							            "elements": [{"localRef": "el-a-heading", "kind": "heading", "role": "title", "contentIntent": "Welcome"}]
							          }
							        ]
							      }
							    ]
							  },
							  "designSpecification": {
							    "colors": [{"role": "primary", "value": "#2f4f2f"}],
							    "typography": [{"role": "heading", "fontFamily": "Fraunces", "fontWeight": 600, "fontSizeRem": 2.2, "lineHeight": 1.2}],
							    "spacing": [{"role": "section", "valueRem": 3}],
							    "layout": {"contentWidth": "narrow", "density": "spacious", "pageGutterRem": 1.5, "sectionGapRem": 3, "gridIntent": "single column"},
							    "uiPatterns": [],
							    "imagery": {"direction": "warm, natural tones", "treatment": "soft-edged photography"},
							    "responsive": {
							      "navigationBehavior": "collapse into a menu icon", "contentStacking": "vertical", "typeScaling": "fluid clamp()",
							      "spacingAdjustment": "reduce by a third", "mediaBehavior": "scale to container"
							    }
							  }
							}
							""");

	private static final Set<String> REPOSITORY_FILES = Set.of("src/pages/Home.tsx");
	private static final String HOME_TSX_CONTENT = "export const Home = () => null;";

	@TempDir
	Path root;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private IntegrationContractRepository integrationContractRepository;

	@Autowired
	private DeveloperExecutionInputAssembler assembler;

	@Autowired
	private DeveloperResultValidator developerResultValidator;

	@Autowired
	private HandoffFreezeGate handoffFreezeGate;

	@Autowired
	private RunnerVerificationEvidencePersister evidencePersister;

	@Autowired
	private WebsiteImplementationCandidateAcceptanceCoordinator coordinator;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void initRealGitRepo() throws IOException, InterruptedException {
		run(root, "git", "init", "--quiet");
		run(root, "git", "config", "user.email", "test@example.com");
		run(root, "git", "config", "user.name", "Test");
	}

	@Test
	void aLocalOnlyFunctionalWebsiteReachesAnAcceptedCandidateThroughTheFullPipeline() throws IOException, InterruptedException {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		String readyResult = readyResult(
				target.path("designArtifactVersionRef").asString(), "prop-a", anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]");

		DeveloperResultValidationResult validation =
				developerResultValidator.validate(readyResult, executionInput, projectId, REPOSITORY_FILES, List.of());
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();

		writeAndTrack("src/pages/Home.tsx", HOME_TSX_CONTENT);
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		AgentExecution execution = startedExecution(projectId);

		RunnerVerificationResult allGatesPass =
				new RunnerVerificationResult(AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream().map(GateResult::pass).toList());
		RunnerVerificationRun run = evidencePersister.persist(execution.getId(), snapshot.snapshotId(), allGatesPass);
		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.PASS);

		WebsiteImplementationCandidate candidate =
				coordinator.accept(execution, projectId, readyResult, executionInput, workspace, snapshot);

		assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).contains(candidate);
		// "Immutable repository state" and "authoritative Runner Verification PASS" are the same
		// verified snapshot, not two separately-computed values that merely happen to agree.
		assertThat(candidate.getRepositoryStateRef()).isEqualTo(run.getRepositoryStateRef()).isEqualTo(snapshot.snapshotId());
	}

	@Test
	void anAuthorizedIntegrationContractProducesAnImplementedBoundBindingAndAnAcceptedCandidate()
			throws IOException, InterruptedException {
		UUID projectId = seedProject();
		integrationContractRepository.saveAndFlush(new IntegrationContract(
				projectId,
				"booking-provider",
				1,
				"""
				{"integrationContractVersion":1,"interfaceName":"BookingProvider",\
				"authorizedOperations":[{"operationName":"createBooking","requestShape":{},"responseShape":{}}],\
				"allowedRuntimeTargets":["https://api.booking.example.com"]}"""));
		String executionInput = assemble(projectId, 3);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		String readyResult = readyResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-a",
				anchorsJson(),
				bindingsJson("IMPLEMENTED_BOUND", "\"integrationContractRef\": \"booking-provider\""),
				"[]");

		DeveloperResultValidationResult validation =
				developerResultValidator.validate(readyResult, executionInput, projectId, REPOSITORY_FILES, List.of());
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();

		WebsiteImplementationCandidate candidate = acceptThroughFullVerification(projectId, readyResult, executionInput);

		assertThat(candidate.getFunctionalBindings()).contains("IMPLEMENTED_BOUND").contains("booking-provider");
	}

	@Test
	void aMissingExternalBindingProducesAValidUnboundResultThatIsStillCandidateEligible() throws IOException, InterruptedException {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		// No Integration Contract exists for this project at all - the binding is honestly
		// reported UNBOUND with the exact blocker code the schema names for that case, never
		// silently upgraded to IMPLEMENTED_BOUND or dropped.
		String unboundResult = readyResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-a",
				anchorsJson(),
				bindingsJson("UNBOUND", "\"blockerCode\": \"MISSING_INTEGRATION_CONTRACT\""),
				"[]");

		DeveloperResultValidationResult validation =
				developerResultValidator.validate(unboundResult, executionInput, projectId, REPOSITORY_FILES, List.of());
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();

		WebsiteImplementationCandidate candidate = acceptThroughFullVerification(projectId, unboundResult, executionInput);

		assertThat(candidate.getFunctionalBindings()).contains("UNBOUND").contains("MISSING_INTEGRATION_CONTRACT");
	}

	@Test
	void missingUpstreamInformationIsRecordedExplicitlyNeverFabricatedAndStillReachesAValidHandoff()
			throws IOException, InterruptedException {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		String honestGapSummary = "Maximum party size for online booking is not known from any canonical input.";
		String unresolvedIssues =
				"""
				[{"code": "MISSING_UPSTREAM_INFORMATION", "relatedRequirementRefs": ["req-func-1"], \
				"relatedDesignLocalRefs": [], "relatedIntegrationContractRefs": [], "summary": "%s"}]"""
						.formatted(honestGapSummary);
		String readyResult = readyResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-a",
				anchorsJson(),
				bindingsJson("UNBOUND", "\"blockerCode\": \"MISSING_UPSTREAM_INFORMATION\""),
				unresolvedIssues);

		DeveloperResultValidationResult validation =
				developerResultValidator.validate(readyResult, executionInput, projectId, REPOSITORY_FILES, List.of());
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();

		WebsiteImplementationCandidate candidate = acceptThroughFullVerification(projectId, readyResult, executionInput);

		// The gap is persisted verbatim, not replaced by an invented capacity figure.
		assertThat(candidate.getUnresolvedIssues()).contains(honestGapSummary);
	}

	@Test
	void aGenuineUpstreamBlockerProducesNoAcceptedCandidateForThatExecution() {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		// UPSTREAM_CONFLICT needs no supporting ToolExecution evidence to validate (unlike
		// TOOL_CAPABILITY_MISSING/RUNTIME_CAPABILITY_MISSING) - a genuine, non-fabricable blocker.
		String blocked = blockedResult(
				target.path("designArtifactVersionRef").asString(),
				"prop-a",
				"""
				[{"code": "UPSTREAM_CONFLICT", "relatedRequirementRefs": ["req-func-1"], "relatedDesignLocalRefs": [], \
				"relatedIntegrationContractRefs": [], "diagnosticSummary": "Two canonical sources disagree on whether bookings require a deposit"}]""");

		DeveloperResultValidationResult validation =
				developerResultValidator.validate(blocked, executionInput, projectId, REPOSITORY_FILES, List.of());
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();

		AgentExecution execution = startedExecution(projectId);
		execution.block("Two canonical sources disagree on whether bookings require a deposit");
		agentExecutionRepository.saveAndFlush(execution);

		assertThat(agentExecutionRepository.findById(execution.getId()).orElseThrow().getStatus())
				.isEqualTo(AgentExecutionStatus.BLOCKED);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void exhaustingTheCorrectionBudgetAfterAGenuineVerificationFailureEndsFailedWithNoCandidate()
			throws IOException, InterruptedException {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 0);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		String readyResult = readyResult(
				target.path("designArtifactVersionRef").asString(), "prop-a", anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]");
		DeveloperResultValidationResult validation =
				developerResultValidator.validate(readyResult, executionInput, projectId, REPOSITORY_FILES, List.of());
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();

		writeAndTrack("src/pages/Home.tsx", HOME_TSX_CONTENT);
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		AgentExecution execution = startedExecution(projectId);

		RunnerVerificationResult typecheckFails = new RunnerVerificationResult(List.of(
				GateResult.pass("repository-dependency-integrity (gate 1)"),
				GateResult.pass("clean-policy-compliant-install (gate 2)"),
				GateResult.fail("typecheck (gate 3)", "exited 1: TS2322")));
		RunnerVerificationRun run = evidencePersister.persist(execution.getId(), snapshot.snapshotId(), typecheckFails);
		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.FAIL);

		// This execution's own developer-execution-input.v1 payload carries maxCorrectionCycles: 0
		// - budget exhaustion on the very first Developer-owned failure ends FAILED, never BLOCKED
		// (AIW-150's own rule), and never reaches the promoter at all.
		boolean anotherCycleAuthorized =
				execution.authorizeCorrectionCycleOrFail(0, "typecheck failed: exited 1: TS2322");
		agentExecutionRepository.saveAndFlush(execution);

		assertThat(anotherCycleAuthorized).isFalse();
		assertThat(agentExecutionRepository.findById(execution.getId()).orElseThrow().getStatus())
				.isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void aSandboxInfrastructureErrorLeavesTheWorkspaceUntouchedAndProducesNoCandidate() throws IOException, InterruptedException {
		UUID projectId = seedProject();
		writeAndTrack("src/pages/Home.tsx", HOME_TSX_CONTENT);
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		AgentExecution execution = startedExecution(projectId);

		RunnerVerificationResult sandboxError = new RunnerVerificationResult(List.of(
				GateResult.pass("repository-dependency-integrity (gate 1)"),
				GateResult.error("clean-policy-compliant-install (gate 2)", "sandbox failed to run the install task: timed out")));
		RunnerVerificationRun run = evidencePersister.persist(execution.getId(), snapshot.snapshotId(), sandboxError);
		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.ERROR);

		execution.error("sandbox failed to run the install task: timed out");
		agentExecutionRepository.saveAndFlush(execution);

		assertThat(agentExecutionRepository.findById(execution.getId()).orElseThrow().getStatus())
				.isEqualTo(AgentExecutionStatus.ERROR);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).isEmpty();
		// Infrastructure malfunction, not a Developer-owned source edit - nothing in this path
		// ever wrote to the workspace, so its tracked content is exactly what it was before.
		assertThat(Files.readString(root.resolve("src/pages/Home.tsx"))).isEqualTo(HOME_TSX_CONTENT);
	}

	@Test
	void aSecretCredentialScanFailureNeverReachesAnAcceptedCandidate() throws IOException, InterruptedException {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		String readyResult = readyResult(
				target.path("designArtifactVersionRef").asString(), "prop-a", anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]");
		DeveloperResultValidationResult validation =
				developerResultValidator.validate(readyResult, executionInput, projectId, REPOSITORY_FILES, List.of());
		assertThat(validation.valid()).as(validation.issues().toString()).isTrue();

		writeAndTrack("src/pages/Home.tsx", HOME_TSX_CONTENT);
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		AgentExecution execution = startedExecution(projectId);

		RunnerVerificationResult secretsLeaked = new RunnerVerificationResult(
				AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream()
						.map(name -> "secret-credential-scan (gate 13)".equals(name)
								? GateResult.fail(name, "a private key was found committed at src/config/keys.pem")
								: GateResult.pass(name))
						// Gate 13 fails fail-fast in the real verifier - later gates never actually run,
						// so this fixture only supplies gates up to and including the failing one.
						.limit(13)
						.toList());
		RunnerVerificationRun run = evidencePersister.persist(execution.getId(), snapshot.snapshotId(), secretsLeaked);
		assertThat(run.getOutcome()).isEqualTo(VerificationOutcome.FAIL);

		// A FAIL verification run never reaches the promoter at all - the only path to a
		// Candidate is through the Coordinator once verification has genuinely PASSed, and
		// nothing here ever calls it.
		execution.authorizeCorrectionCycleOrFail(0, "secret-credential-scan failed: a private key was committed");
		agentExecutionRepository.saveAndFlush(execution);

		assertThat(agentExecutionRepository.findById(execution.getId()).orElseThrow().getStatus())
				.isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void aRepositoryStateMismatchAfterAPassingVerificationIsRefusedNeverSilentlyAccepted() throws IOException, InterruptedException {
		UUID projectId = seedProject();
		String executionInput = assemble(projectId, 3);
		JsonNode target = objectMapper.readTree(executionInput).path("targetDesign");
		String readyResult = readyResult(
				target.path("designArtifactVersionRef").asString(), "prop-a", anchorsJson(), bindingsJson("IMPLEMENTED_LOCAL"), "[]");

		writeAndTrack("src/pages/Home.tsx", HOME_TSX_CONTENT);
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		AgentExecution execution = startedExecution(projectId);
		RunnerVerificationResult allGatesPass =
				new RunnerVerificationResult(AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream().map(GateResult::pass).toList());
		evidencePersister.persist(execution.getId(), snapshot.snapshotId(), allGatesPass);
		// Something mutates tracked source between the PASS-ing verification and acceptance.
		Files.writeString(root.resolve("src/pages/Home.tsx"), "export const Home = () => \"mutated\";");
		run(root, "git", "add", "src/pages/Home.tsx");

		assertThatThrownBy(() -> coordinator.accept(execution, projectId, readyResult, executionInput, workspace, snapshot))
				.isInstanceOf(RepositoryStateMismatchException.class);

		assertThat(agentExecutionRepository.findById(execution.getId()).orElseThrow().getStatus())
				.isEqualTo(AgentExecutionStatus.ERROR);
		assertThat(candidateRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	private WebsiteImplementationCandidate acceptThroughFullVerification(
			UUID projectId, String developerResultJson, String executionInputJson) throws IOException, InterruptedException {
		writeAndTrack("src/pages/Home.tsx", HOME_TSX_CONTENT);
		Workspace workspace = new Workspace(root);
		FrozenHandoffSnapshot snapshot = handoffFreezeGate.freeze(workspace);
		AgentExecution execution = startedExecution(projectId);

		RunnerVerificationResult allGatesPass =
				new RunnerVerificationResult(AuthoritativeRunnerVerifier.MANDATORY_GATE_NAMES.stream().map(GateResult::pass).toList());
		evidencePersister.persist(execution.getId(), snapshot.snapshotId(), allGatesPass);

		return coordinator.accept(execution, projectId, developerResultJson, executionInputJson, workspace, snapshot);
	}

	private String assemble(UUID projectId, int maxCorrectionCycles) {
		return assembler.assemble(projectId, "prop-a", "commit-sha-fixture", maxCorrectionCycles);
	}

	private String anchorsJson() {
		return """
				[
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx", "symbol": "HeroSection"}]}
				]""";
	}

	private String bindingsJson(String status) {
		return bindingsJson(status, null);
	}

	private String bindingsJson(String status, String extraField) {
		String extra = extraField == null ? "" : ", " + extraField;
		return """
				[{"requirementRef": "req-func-1", "designLocalRefs": ["sec-a-hero"], "status": "%s"%s}]"""
				.formatted(status, extra);
	}

	private String readyResult(
			String designArtifactVersionRef, String proposalLocalRef, String anchors, String bindings, String unresolvedIssues) {
		return """
				{
				  "resultType": "IMPLEMENTATION_READY",
				  "targetDesign": {"designArtifactVersionRef": "%s", "proposalLocalRef": "%s"},
				  "implementationSummary": "Implemented the home page hero.",
				  "implementationAnchors": %s,
				  "functionalBindings": %s,
				  "unresolvedIssues": %s
				}
				""".formatted(designArtifactVersionRef, proposalLocalRef, anchors, bindings, unresolvedIssues);
	}

	private String blockedResult(String designArtifactVersionRef, String proposalLocalRef, String blockers) {
		return """
				{
				  "resultType": "BLOCKED",
				  "targetDesign": {"designArtifactVersionRef": "%s", "proposalLocalRef": "%s"},
				  "blockers": %s
				}
				""".formatted(designArtifactVersionRef, proposalLocalRef, blockers);
	}

	private AgentExecution startedExecution(UUID projectId) {
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "developer-agent", 1));
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private UUID seedProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		persistArtifactVersion(project.getId(), "design-proposal-set", PROPOSAL_SET_JSON);
		return project.getId();
	}

	private void persistArtifactVersion(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), content));
	}

	private void writeAndTrack(String relativePath, String content) throws IOException, InterruptedException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		run(root, "git", "add", relativePath);
	}

	private static void run(Path cwd, String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Command " + List.of(command) + " failed with exit code " + exitCode);
		}
	}
}
