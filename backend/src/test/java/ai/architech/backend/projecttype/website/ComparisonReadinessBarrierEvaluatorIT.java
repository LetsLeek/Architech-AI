package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-177's own named scenarios: 3/3 PASS, 2/3 PASS, stale result, and a
 * remediated single variant leaving its siblings untouched.
 */
@SpringBootTest
@Transactional
class ComparisonReadinessBarrierEvaluatorIT {

	private static final String PROFILE_REF = "website-qa-comparison-readiness@1.0.0";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private ComparisonReadinessBarrierRepository barrierRepository;

	@Autowired
	private ComparisonReadinessSlotRepository slotRepository;

	@Autowired
	private ComparisonReadinessBarrierEvaluator evaluator;

	@Test
	void threeOfThreePassMakesTheBarrierFullyEligible() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ComparisonReadinessBarrier barrier = barrierRepository.saveAndFlush(new ComparisonReadinessBarrier(project.getId()));

		seedPassingSlot(barrier, project, "prop-a");
		seedPassingSlot(barrier, project, "prop-b");
		seedPassingSlot(barrier, project, "prop-c");

		ComparisonReadinessEvaluation evaluation = evaluator.evaluate(barrier.getId(), PROFILE_REF);

		assertThat(evaluation.allEligible()).isTrue();
		assertThat(evaluation.variants()).hasSize(3).allMatch(VariantEligibility::eligible);
	}

	@Test
	void twoOfThreePassLeavesTheBarrierNotEligible() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ComparisonReadinessBarrier barrier = barrierRepository.saveAndFlush(new ComparisonReadinessBarrier(project.getId()));

		seedPassingSlot(barrier, project, "prop-a");
		seedPassingSlot(barrier, project, "prop-b");
		seedFailingSlot(barrier, project, "prop-c");

		ComparisonReadinessEvaluation evaluation = evaluator.evaluate(barrier.getId(), PROFILE_REF);

		assertThat(evaluation.allEligible()).isFalse();
		// The failed/unready variant still appears - never silently removed from the comparison.
		assertThat(evaluation.variants()).hasSize(3);
		assertThat(evaluation.variants()).filteredOn(v -> v.variantLineageRef().equals("prop-c")).allMatch(v -> !v.eligible());
	}

	@Test
	void aStaleResultAgainstAReplacedCandidateNeverQualifiesTheNewOne() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ComparisonReadinessBarrier barrier = barrierRepository.saveAndFlush(new ComparisonReadinessBarrier(project.getId()));

		// Original Candidate PASSed comparison readiness...
		WebsiteImplementationCandidate originalCandidate = seedCandidate(project, "prop-a");
		seedQaResult(originalCandidate, "PASS");
		ComparisonReadinessSlot slot =
				slotRepository.saveAndFlush(new ComparisonReadinessSlot(barrier.getId(), "prop-a", originalCandidate.getId()));

		// ...but the slot now points at a brand-new, remediated Candidate that has no QA Result
		// of its own yet - the original PASS must not carry over.
		WebsiteImplementationCandidate remediatedCandidate = seedCandidate(project, "prop-a");
		slot.updateCurrentCandidate(remediatedCandidate.getId());
		slotRepository.saveAndFlush(slot);

		ComparisonReadinessEvaluation evaluation = evaluator.evaluate(barrier.getId(), PROFILE_REF);

		assertThat(evaluation.allEligible()).isFalse();
		assertThat(evaluation.variants()).hasSize(1);
		assertThat(evaluation.variants().get(0).eligible()).isFalse();
		assertThat(evaluation.variants().get(0).currentCandidateId()).isEqualTo(remediatedCandidate.getId());
	}

	@Test
	void remediatingOneVariantLeavesItsSiblingsUntouchedAndStillEligible() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		ComparisonReadinessBarrier barrier = barrierRepository.saveAndFlush(new ComparisonReadinessBarrier(project.getId()));

		ComparisonReadinessSlot slotA = seedPassingSlot(barrier, project, "prop-a");
		ComparisonReadinessSlot slotB = seedPassingSlot(barrier, project, "prop-b");
		seedFailingSlot(barrier, project, "prop-c");

		ComparisonReadinessEvaluation before = evaluator.evaluate(barrier.getId(), PROFILE_REF);
		assertThat(before.allEligible()).isFalse();

		// Remediate only "prop-c" with a fresh, passing Candidate.
		WebsiteImplementationCandidate remediatedC = seedCandidate(project, "prop-c");
		seedQaResult(remediatedC, "PASS");
		ComparisonReadinessSlot slotC = slotRepository.findByBarrierIdOrderByCreatedAtAsc(barrier.getId()).stream()
				.filter(s -> s.getVariantLineageRef().equals("prop-c"))
				.findFirst()
				.orElseThrow();
		slotC.updateCurrentCandidate(remediatedC.getId());
		slotRepository.saveAndFlush(slotC);

		ComparisonReadinessEvaluation after = evaluator.evaluate(barrier.getId(), PROFILE_REF);

		assertThat(after.allEligible()).isTrue();
		// The other two slots' own Candidate pointers were never touched.
		assertThat(slotRepository.findById(slotA.getId()).orElseThrow().getCurrentCandidateId())
				.isEqualTo(slotA.getCurrentCandidateId());
		assertThat(slotRepository.findById(slotB.getId()).orElseThrow().getCurrentCandidateId())
				.isEqualTo(slotB.getCurrentCandidateId());
	}

	private ComparisonReadinessSlot seedPassingSlot(ComparisonReadinessBarrier barrier, Project project, String variantLineageRef) {
		WebsiteImplementationCandidate candidate = seedCandidate(project, variantLineageRef);
		seedQaResult(candidate, "PASS");
		return slotRepository.saveAndFlush(new ComparisonReadinessSlot(barrier.getId(), variantLineageRef, candidate.getId()));
	}

	private ComparisonReadinessSlot seedFailingSlot(ComparisonReadinessBarrier barrier, Project project, String variantLineageRef) {
		WebsiteImplementationCandidate candidate = seedCandidate(project, variantLineageRef);
		seedQaResult(candidate, "HOLD");
		return slotRepository.saveAndFlush(new ComparisonReadinessSlot(barrier.getId(), variantLineageRef, candidate.getId()));
	}

	private WebsiteImplementationCandidate seedCandidate(Project project, String proposalLocalRef) {
		AgentExecution developerExecution = new AgentExecution(project.getId(), "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				project.getId(), developerExecution.getId(), "design-v1", proposalLocalRef, "runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));
	}

	private QaResult seedQaResult(WebsiteImplementationCandidate candidate, String gateOutcome) {
		AgentExecution qaAgentExecution = new AgentExecution(candidate.getProjectId(), "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution execution = qaExecutionRepository.saveAndFlush(
				new QaExecution(qaAgentExecution.getId(), candidate.getId(), PROFILE_REF, null, "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(execution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(execution.getId()));
		return qaResultRepository.saveAndFlush(new QaResult(
				UUID.randomUUID(), execution.getId(), candidate.getId(), PROFILE_REF, inputSnapshot.getId(),
				"COMPLETE", "[]", "[]", "[]", "[]", "[]", "[]", gateOutcome, "[]",
				evidenceManifest.getId(), "{\"qaSystemVersion\":\"1.0.0\"}"));
	}
}
