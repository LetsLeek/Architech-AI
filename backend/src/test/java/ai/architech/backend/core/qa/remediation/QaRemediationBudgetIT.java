package ai.architech.backend.core.qa.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.profiles.QaProfileType;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof that {@link QaRemediationBudget} genuinely persists, enforces its own
 * unique (project, lineage, stage) identity, and rejects a stale concurrent write via real JPA
 * optimistic locking (AIW-181).
 */
@SpringBootTest
@Transactional
class QaRemediationBudgetIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private QaRemediationBudgetRepository budgetRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void persistsAndResolvesByProjectLineageAndStage() {
		UUID projectId = projectRepository.saveAndFlush(new Project("website")).getId();
		QaRemediationBudget saved =
				budgetRepository.saveAndFlush(new QaRemediationBudget(projectId, "prop-a", QaProfileType.COMPARISON_READINESS));

		var resolved = budgetRepository.findByProjectIdAndVariantLineageRefAndStage(projectId, "prop-a", QaProfileType.COMPARISON_READINESS);

		assertThat(resolved).isPresent();
		assertThat(resolved.get().getId()).isEqualTo(saved.getId());
		assertThat(resolved.get().getProjectId()).isEqualTo(projectId);
		assertThat(resolved.get().getVariantLineageRef()).isEqualTo("prop-a");
		assertThat(resolved.get().getStage()).isEqualTo(QaProfileType.COMPARISON_READINESS);
		assertThat(resolved.get().getRemediationCyclesUsed()).isZero();
		assertThat(resolved.get().getVersion()).isZero();
		assertThat(resolved.get().getCreatedAt()).isNotNull();
	}

	@Test
	void aStaleConcurrentUpdateIsRejectedByOptimisticLocking() {
		UUID projectId = projectRepository.saveAndFlush(new Project("website")).getId();
		QaRemediationBudget saved =
				budgetRepository.saveAndFlush(new QaRemediationBudget(projectId, "prop-a", QaProfileType.COMPARISON_READINESS));
		entityManager.clear();

		QaRemediationBudget firstReader = budgetRepository.findById(saved.getId()).orElseThrow();
		QaRemediationBudget secondReader = budgetRepository.findById(saved.getId()).orElseThrow();
		entityManager.clear();

		firstReader.useRemediationCycle(5);
		budgetRepository.saveAndFlush(firstReader);
		entityManager.clear();

		secondReader.useRemediationCycle(5);
		assertThatThrownBy(() -> budgetRepository.saveAndFlush(secondReader))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}
}
