package ai.architech.backend.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IntegrationContractRepositoryIT {

	private static final String SAFE_CONTENT = """
			{"integrationContractVersion":1,"interfaceName":"BookingProvider",\
			"authorizedOperations":[{"operationName":"createBooking","requestShape":{},"responseShape":{}}],\
			"allowedRuntimeTargets":["https://api.booking.example.com"]}""";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private IntegrationContractRepository integrationContractRepository;

	@Test
	void savesAndReloadsAContractByProjectAndRef() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		integrationContractRepository.saveAndFlush(
				new IntegrationContract(project.getId(), "booking-provider", 1, SAFE_CONTENT));

		IntegrationContract reloaded = integrationContractRepository
				.findByProjectIdAndContractRef(project.getId(), "booking-provider")
				.orElseThrow();
		assertThat(reloaded.getVersion()).isEqualTo(1);
		assertThat(reloaded.getSafeContractContent()).isEqualTo(SAFE_CONTENT);
		assertThat(reloaded.getCreatedAt()).isNotNull();
	}

	@Test
	void findsNothingForAnUnknownContractRef() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		assertThat(integrationContractRepository.findByProjectIdAndContractRef(project.getId(), "unknown-provider"))
				.isEmpty();
	}

	@Test
	void neverResolvesAContractAcrossProjectsEvenWithTheSameRef() {
		Project projectA = projectRepository.saveAndFlush(new Project("website"));
		Project projectB = projectRepository.saveAndFlush(new Project("website"));
		integrationContractRepository.saveAndFlush(
				new IntegrationContract(projectA.getId(), "shared-name", 1, SAFE_CONTENT));

		assertThat(integrationContractRepository.findByProjectIdAndContractRef(projectB.getId(), "shared-name"))
				.isEmpty();
	}

	@Test
	void rejectsADuplicateContractRefWithinTheSameProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		integrationContractRepository.saveAndFlush(
				new IntegrationContract(project.getId(), "booking-provider", 1, SAFE_CONTENT));

		assertThatThrownBy(() -> integrationContractRepository.saveAndFlush(
						new IntegrationContract(project.getId(), "booking-provider", 2, SAFE_CONTENT)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
