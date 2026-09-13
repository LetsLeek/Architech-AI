package ai.architech.backend.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IntegrationContractResolverIT {

	private static final String VALID_SAFE_CONTENT = """
			{"integrationContractVersion":1,"interfaceName":"BookingProvider",\
			"authorizedOperations":[{"operationName":"createBooking","requestShape":{},"responseShape":{}}],\
			"allowedRuntimeTargets":["https://api.booking.example.com"]}""";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private IntegrationContractRepository integrationContractRepository;

	@Autowired
	private IntegrationContractResolver integrationContractResolver;

	@Test
	void resolvesAValidBoundContractAsAuthorized() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		integrationContractRepository.saveAndFlush(
				new IntegrationContract(project.getId(), "booking-provider", 1, VALID_SAFE_CONTENT));

		IntegrationContractResolution resolution = integrationContractResolver.resolve(project.getId(), "booking-provider");

		assertThat(resolution).isInstanceOfSatisfying(IntegrationContractResolution.Authorized.class, authorized ->
				assertThat(authorized.contract().getContractRef()).isEqualTo("booking-provider"));
	}

	@Test
	void resolvesAMissingContractAsMissing() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		IntegrationContractResolution resolution = integrationContractResolver.resolve(project.getId(), "never-authorized");

		assertThat(resolution).isInstanceOf(IntegrationContractResolution.Missing.class);
	}

	@Test
	void resolvesAStructurallyInvalidContractAsInvalid() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		String missingRequiredField = """
				{"integrationContractVersion":1,"interfaceName":"BookingProvider"}""";
		integrationContractRepository.saveAndFlush(
				new IntegrationContract(project.getId(), "broken-provider", 1, missingRequiredField));

		IntegrationContractResolution resolution = integrationContractResolver.resolve(project.getId(), "broken-provider");

		assertThat(resolution).isInstanceOf(IntegrationContractResolution.Invalid.class);
	}

	@Test
	void neverResolvesAContractAuthorizedOnlyForAnUnrelatedProject() {
		Project owningProject = projectRepository.saveAndFlush(new Project("website"));
		Project otherProject = projectRepository.saveAndFlush(new Project("website"));
		integrationContractRepository.saveAndFlush(
				new IntegrationContract(owningProject.getId(), "booking-provider", 1, VALID_SAFE_CONTENT));

		IntegrationContractResolution resolution = integrationContractResolver.resolve(otherProject.getId(), "booking-provider");

		assertThat(resolution).isInstanceOf(IntegrationContractResolution.Missing.class);
	}

	@Test
	void rejectsAttemptedSecretLeakageThroughAnUnauthorizedContentField() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		String contentWithSecretShapedField = """
				{"integrationContractVersion":1,"interfaceName":"BookingProvider",\
				"authorizedOperations":[{"operationName":"createBooking","requestShape":{},"responseShape":{}}],\
				"allowedRuntimeTargets":["https://api.booking.example.com"],\
				"adminApiKey":"sk-should-never-be-here"}""";
		integrationContractRepository.saveAndFlush(
				new IntegrationContract(project.getId(), "leaky-provider", 1, contentWithSecretShapedField));

		IntegrationContractResolution resolution = integrationContractResolver.resolve(project.getId(), "leaky-provider");

		// additionalProperties: false on the safe-view schema means an unrecognized field (here,
		// masquerading as a secret) makes the whole contract invalid rather than silently passing
		// through to the Developer.
		assertThat(resolution).isInstanceOf(IntegrationContractResolution.Invalid.class);
	}

	@Test
	void emptyIntegrationContextHasNothingToResolve() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		assertThat(integrationContractResolver.resolve(project.getId(), "anything"))
				.isInstanceOf(IntegrationContractResolution.Missing.class);
	}
}
