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
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real Postgres + real git proof of AIW-146's own acceptance criteria - the fan-out, isolation,
 * bounded retry/escalation and aggregate-completion semantics, without needing the still-unbuilt
 * agentic tool-calling loop (see {@link InitialGenerationOrchestrator}'s own class javadoc):
 * every sibling execution here is driven to a terminal status directly, exactly the way a real
 * Developer Runner eventually will, so this suite exercises everything downstream of "the model
 * actually ran" without needing it to.
 */
@SpringBootTest
@Transactional
class InitialGenerationOrchestratorIT {

	private static final String CUSTOMER_PROFILE =
			"""
			{"business": {"name": "Green Leaf Cafe"}, "contact": {"phone": "+43 1 2345678"},
			 "locations": [{"localRef": "cust-loc-1", "name": "Vienna HQ"}], "offerings": [],
			 "openingHours": [], "socialLinks": [], "providedClaims": [], "unknowns": [], "conflicts": [], "provenance": []}""";

	private static final String WEBSITE_REQUIREMENTS =
			"""
			{"goals": [{"localRef": "req-goal-1", "description": "Grow local visibility", "strength": "must", "sourceRefs": ["s1"]}],
			 "targetAudiences": [], "contentRequirements": [], "functionalRequirements": [], "languages": [], "constraints": [],
			 "unknowns": [], "conflicts": []}""";

	private static String proposal(String localRef) {
		return
				"""
				{"localRef": "%s", "name": "Warm Minimal", "concept": "A calm, minimal layout.",
				 "websitePlan": {"requirementRefs": ["req-goal-1"], "pages": [
				   {"localRef": "page-a-home", "name": "Home", "route": "/", "purpose": "Introduce the cafe",
				    "requirementRefs": ["req-goal-1"], "sections": []}
				 ]},
				 "designSpecification": {
				   "colors": [{"role": "primary", "value": "#2f4f2f"}],
				   "typography": [{"role": "heading", "fontFamily": "Fraunces", "fontWeight": 600, "fontSizeRem": 2.2, "lineHeight": 1.2}],
				   "spacing": [{"role": "section", "valueRem": 3}],
				   "layout": {"contentWidth": "narrow", "density": "spacious", "pageGutterRem": 1.5, "sectionGapRem": 3, "gridIntent": "single column"},
				   "uiPatterns": [], "imagery": {"direction": "warm", "treatment": "soft-edged"},
				   "responsive": {"navigationBehavior": "collapse", "contentStacking": "vertical", "typeScaling": "fluid",
				     "spacingAdjustment": "reduce", "mediaBehavior": "scale"}}}"""
						.formatted(localRef);
	}

	private static String proposalSet(String... localRefs) {
		return "{\"proposals\": [" + String.join(",", Arrays.stream(localRefs).map(InitialGenerationOrchestratorIT::proposal).toList()) + "]}";
	}

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private InitialGenerationBatchRepository batchRepository;

	@Autowired
	private InitialGenerationSlotRepository slotRepository;

	@Autowired
	private InitialGenerationOrchestrator orchestrator;

	@Autowired
	private DeveloperExecutionInputAssembler developerExecutionInputAssembler;

	@Test
	void startCreatesThreeProposalScopedSlotsEachWithItsOwnIsolatedWorkspaceFromTheSameBase(
			@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");

		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);

		List<InitialGenerationSlot> slots = slotRepository.findByBatchIdOrderByProposalLocalRefAsc(batch.getId());
		assertThat(slots).extracting(InitialGenerationSlot::getProposalLocalRef).containsExactly("prop-a", "prop-b", "prop-c");

		List<UUID> executionIds = slots.stream().map(InitialGenerationSlot::getCurrentAgentExecutionId).distinct().toList();
		assertThat(executionIds).hasSize(3);
		for (UUID executionId : executionIds) {
			AgentExecution execution = agentExecutionRepository.findById(executionId).orElseThrow();
			assertThat(execution.getAgentId()).isEqualTo("developer-agent");
			assertThat(execution.getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);
			assertThat(workspacesRoot.resolve(executionId.toString()).resolve(".git")).isDirectory();
			assertThat(workspacesRoot.resolve(executionId.toString()).resolve("package.json")).exists();
		}
	}

	@Test
	void rejectsADesignProposalSetWithoutExactlyThreeProposals(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b");

		assertThatThrownBy(() -> orchestrator.start(projectId, repositoryRoot, workspacesRoot))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("exactly 3 proposals");
	}

	@Test
	void evaluateStaysInProgressUntilAllThreeSucceedThenReportsCompleteWithAllThreeCandidates(
			@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);
		List<InitialGenerationSlot> slots = slotRepository.findByBatchIdOrderByProposalLocalRefAsc(batch.getId());

		succeedWithCandidate(projectId, slots.get(0));
		succeedWithCandidate(projectId, slots.get(1));
		assertThat(orchestrator.evaluate(batch.getId()).status()).isEqualTo(InitialGenerationBatchStatus.IN_PROGRESS);

		succeedWithCandidate(projectId, slots.get(2));
		InitialGenerationBatchOutcome outcome = orchestrator.evaluate(batch.getId());

		assertThat(outcome.status()).isEqualTo(InitialGenerationBatchStatus.COMPLETE);
		assertThat(outcome.candidateIdsByProposalLocalRef()).containsOnlyKeys("prop-a", "prop-b", "prop-c");
		assertThat(batchRepository.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(InitialGenerationBatchStatus.COMPLETE);
	}

	@Test
	void oneSiblingFailureCannotMutateOrInvalidateAnotherSuccessfulCandidate(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);
		List<InitialGenerationSlot> slots = slotRepository.findByBatchIdOrderByProposalLocalRefAsc(batch.getId());

		WebsiteImplementationCandidate candidateA = succeedWithCandidate(projectId, slots.get(0));
		WebsiteImplementationCandidate candidateB = succeedWithCandidate(projectId, slots.get(1));
		failExecution(slots.get(2));

		orchestrator.retrySlotOrEscalate(
				batch.getId(), "prop-c", "RESULT_VALIDATION_FAILURE", "output-contract validation failed", repositoryRoot, workspacesRoot);

		assertThat(candidateRepository.findById(candidateA.getId())).contains(candidateA);
		assertThat(candidateRepository.findById(candidateB.getId())).contains(candidateB);
		InitialGenerationSlot slotA = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-a").orElseThrow();
		InitialGenerationSlot slotB = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-b").orElseThrow();
		assertThat(slotA.getCurrentAgentExecutionId()).isEqualTo(slots.get(0).getCurrentAgentExecutionId());
		assertThat(slotB.getCurrentAgentExecutionId()).isEqualTo(slots.get(1).getCurrentAgentExecutionId());
	}

	@Test
	void retryingAFailedSiblingCreatesANewLinkedExecutionAndAdvancesOnlyThatSlot(
			@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);
		InitialGenerationSlot slotC = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-c").orElseThrow();
		UUID priorExecutionId = slotC.getCurrentAgentExecutionId();
		failExecution(slotC);

		boolean advanced = orchestrator.retrySlotOrEscalate(
				batch.getId(), "prop-c", "RESULT_VALIDATION_FAILURE", "output-contract validation failed", repositoryRoot, workspacesRoot);

		assertThat(advanced).isTrue();
		InitialGenerationSlot reloaded = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-c").orElseThrow();
		assertThat(reloaded.getCurrentAgentExecutionId()).isNotEqualTo(priorExecutionId);
		assertThat(reloaded.getRetriesUsed()).isEqualTo(1);
		AgentExecution retryExecution = agentExecutionRepository.findById(reloaded.getCurrentAgentExecutionId()).orElseThrow();
		assertThat(retryExecution.getRetryOfExecutionId()).isEqualTo(priorExecutionId);
		assertThat(batchRepository.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(InitialGenerationBatchStatus.IN_PROGRESS);
	}

	@Test
	void anInfraErroredSiblingCanAlsoBeMechanicallyRetried(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);
		InitialGenerationSlot slotB = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-b").orElseThrow();
		AgentExecution execution = agentExecutionRepository.findById(slotB.getCurrentAgentExecutionId()).orElseThrow();
		execution.error("sandbox failed to start");
		agentExecutionRepository.saveAndFlush(execution);

		boolean advanced = orchestrator.retrySlotOrEscalate(
				batch.getId(), "prop-b", "RUNNER_VERIFICATION_FAILURE", "sandbox infra failure", repositoryRoot, workspacesRoot);

		assertThat(advanced).isTrue();
	}

	@Test
	void aBlockedSiblingCannotBeMechanicallyRetried(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);
		InitialGenerationSlot slotA = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-a").orElseThrow();
		AgentExecution execution = agentExecutionRepository.findById(slotA.getCurrentAgentExecutionId()).orElseThrow();
		execution.block("integration contract required for payment provider is missing");
		agentExecutionRepository.saveAndFlush(execution);

		assertThatThrownBy(() -> orchestrator.retrySlotOrEscalate(
						batch.getId(), "prop-a", "RESULT_VALIDATION_FAILURE", "n/a", repositoryRoot, workspacesRoot))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("BLOCKED");
	}

	@Test
	void explicitEscalationIsReportedByEvaluate(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);

		orchestrator.escalate(batch.getId(), "sibling 'prop-a' semantically BLOCKED - requires human decision");
		InitialGenerationBatchOutcome outcome = orchestrator.evaluate(batch.getId());

		assertThat(outcome.status()).isEqualTo(InitialGenerationBatchStatus.ESCALATED);
		assertThat(outcome.escalationReason()).contains("BLOCKED");
	}

	@Test
	void exhaustingTheRetryBudgetEscalatesTheBatchInsteadOfLoopingForever(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		InitialGenerationBatch batch = orchestrator.start(projectId, repositoryRoot, workspacesRoot);
		// architech.developer.initial-generation.max-sibling-retries is 2 in application.yml.
		for (int attempt = 1; attempt <= 2; attempt++) {
			InitialGenerationSlot slot = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-a").orElseThrow();
			failExecution(slot);
			boolean advanced = orchestrator.retrySlotOrEscalate(
					batch.getId(), "prop-a", "RESULT_VALIDATION_FAILURE", "attempt " + attempt, repositoryRoot, workspacesRoot);
			assertThat(advanced).isTrue();
		}
		InitialGenerationSlot exhaustedSlot = slotRepository.findByBatchIdAndProposalLocalRef(batch.getId(), "prop-a").orElseThrow();
		failExecution(exhaustedSlot);

		boolean advanced = orchestrator.retrySlotOrEscalate(
				batch.getId(), "prop-a", "RESULT_VALIDATION_FAILURE", "final attempt", repositoryRoot, workspacesRoot);

		assertThat(advanced).isFalse();
		InitialGenerationBatch reloaded = batchRepository.findById(batch.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(InitialGenerationBatchStatus.ESCALATED);
		assertThat(reloaded.getEscalationReason()).contains("prop-a").contains("retry budget");
	}

	@Test
	void siblingInputsNeverContainAnotherSiblingsProposal(@TempDir Path repositoryRoot, @TempDir Path workspacesRoot) {
		UUID projectId = seedProject("prop-a", "prop-b", "prop-c");
		orchestrator.start(projectId, repositoryRoot, workspacesRoot);
		// InitialGenerationOrchestrator itself already assembles (and discards) each sibling's
		// input during start() as a fail-fast proof; re-assembling here directly against the same
		// canonical artifacts re-exercises the exact same isolation guarantee for assertion.
		String inputA = developerExecutionInputAssembler.assemble(projectId, "prop-a", "commit-sha-fixture", 2);
		String inputB = developerExecutionInputAssembler.assemble(projectId, "prop-b", "commit-sha-fixture", 2);
		String inputC = developerExecutionInputAssembler.assemble(projectId, "prop-c", "commit-sha-fixture", 2);

		assertThat(inputA).contains("\"localRef\":\"prop-a\"").doesNotContain("\"localRef\":\"prop-b\"").doesNotContain("\"localRef\":\"prop-c\"");
		assertThat(inputB).contains("\"localRef\":\"prop-b\"").doesNotContain("\"localRef\":\"prop-a\"").doesNotContain("\"localRef\":\"prop-c\"");
		assertThat(inputC).contains("\"localRef\":\"prop-c\"").doesNotContain("\"localRef\":\"prop-a\"").doesNotContain("\"localRef\":\"prop-b\"");
	}

	private WebsiteImplementationCandidate succeedWithCandidate(UUID projectId, InitialGenerationSlot slot) {
		AgentExecution execution = agentExecutionRepository.findById(slot.getCurrentAgentExecutionId()).orElseThrow();
		execution.succeed();
		agentExecutionRepository.saveAndFlush(execution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				execution.getId(),
				"design-v1",
				slot.getProposalLocalRef(),
				"runtime-v1",
				"snapshot-hash-" + slot.getProposalLocalRef(),
				"summary",
				"[]",
				"[]",
				"[]"));
	}

	private void failExecution(InitialGenerationSlot slot) {
		AgentExecution execution = agentExecutionRepository.findById(slot.getCurrentAgentExecutionId()).orElseThrow();
		execution.fail("output-contract validation failed");
		agentExecutionRepository.saveAndFlush(execution);
	}

	private UUID seedProject(String... proposalLocalRefs) {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		persistArtifactVersion(project.getId(), "customer-profile", CUSTOMER_PROFILE);
		persistArtifactVersion(project.getId(), "website-requirements", WEBSITE_REQUIREMENTS);
		persistArtifactVersion(project.getId(), "design-proposal-set", proposalSet(proposalLocalRefs));
		return project.getId();
	}

	private void persistArtifactVersion(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, execution.getId(), content));
	}
}
