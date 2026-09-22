package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof that {@link ComparisonReadinessSlot}'s retrofitted {@code @Version} field
 * (AIW-181) genuinely rejects a late/stale concurrent pointer update rather than silently
 * overwriting a newer one.
 */
@SpringBootTest
@Transactional
class ComparisonReadinessSlotOptimisticLockingIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private ComparisonReadinessBarrierRepository barrierRepository;

	@Autowired
	private ComparisonReadinessSlotRepository slotRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void aStaleConcurrentPointerUpdateIsRejected() {
		UUID projectId = projectRepository.saveAndFlush(new Project("website")).getId();
		WebsiteImplementationCandidate firstCandidate = seedCandidate(projectId);
		ComparisonReadinessBarrier barrier = barrierRepository.saveAndFlush(new ComparisonReadinessBarrier(projectId));
		ComparisonReadinessSlot saved =
				slotRepository.saveAndFlush(new ComparisonReadinessSlot(barrier.getId(), "prop-a", firstCandidate.getId()));
		assertThat(saved.getVersion()).isZero();
		entityManager.clear();

		ComparisonReadinessSlot firstReader = slotRepository.findById(saved.getId()).orElseThrow();
		ComparisonReadinessSlot secondReader = slotRepository.findById(saved.getId()).orElseThrow();
		entityManager.clear();

		WebsiteImplementationCandidate secondCandidate = seedCandidate(projectId);
		WebsiteImplementationCandidate thirdCandidate = seedCandidate(projectId);

		firstReader.updateCurrentCandidate(secondCandidate.getId());
		slotRepository.saveAndFlush(firstReader);
		entityManager.clear();

		secondReader.updateCurrentCandidate(thirdCandidate.getId());
		assertThatThrownBy(() -> slotRepository.saveAndFlush(secondReader)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private WebsiteImplementationCandidate seedCandidate(UUID projectId) {
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), "design-v1", "prop-a", "runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));
	}
}
