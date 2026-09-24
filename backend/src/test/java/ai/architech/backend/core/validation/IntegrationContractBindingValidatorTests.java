package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.integration.IntegrationContract;
import ai.architech.backend.core.integration.IntegrationContractResolution;
import ai.architech.backend.core.integration.IntegrationContractResolver;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationContractBindingValidatorTests {

	@Mock
	private IntegrationContractResolver integrationContractResolver;

	private IntegrationContractBindingValidator validator;
	private UUID projectId;

	private static final String RESULT_WITH_BOUND_BINDING =
			"""
			{"functionalBindings": [{"requirementRef": "req-func-1", "status": "IMPLEMENTED_BOUND", "integrationContractRef": "booking-provider"}]}""";

	@Test
	void passesWhenTheContractIsAuthorized() {
		validator = new IntegrationContractBindingValidator(integrationContractResolver);
		projectId = UUID.randomUUID();
		IntegrationContract contract = new IntegrationContract(projectId, "booking-provider", 1, "{}");
		when(integrationContractResolver.resolve(eq(projectId), eq("booking-provider")))
				.thenReturn(new IntegrationContractResolution.Authorized(contract));

		assertThat(validator.validate(RESULT_WITH_BOUND_BINDING, projectId).valid()).isTrue();
	}

	@Test
	void failsWhenTheContractIsMissing() {
		validator = new IntegrationContractBindingValidator(integrationContractResolver);
		projectId = UUID.randomUUID();
		when(integrationContractResolver.resolve(any(), any())).thenReturn(new IntegrationContractResolution.Missing());

		DeveloperResultValidationResult result = validator.validate(RESULT_WITH_BOUND_BINDING, projectId);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("MISSING_INTEGRATION_CONTRACT"));
	}

	@Test
	void failsWhenTheContractIsStructurallyInvalid() {
		validator = new IntegrationContractBindingValidator(integrationContractResolver);
		projectId = UUID.randomUUID();
		IntegrationContract contract = new IntegrationContract(projectId, "booking-provider", 1, "{}");
		when(integrationContractResolver.resolve(any(), any()))
				.thenReturn(new IntegrationContractResolution.Invalid(contract, "missing required property"));

		DeveloperResultValidationResult result = validator.validate(RESULT_WITH_BOUND_BINDING, projectId);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("INVALID_INTEGRATION_CONTRACT"));
	}

	@Test
	void neverResolvesAContractForANonBoundBinding() {
		validator = new IntegrationContractBindingValidator(integrationContractResolver);
		projectId = UUID.randomUUID();
		String result = """
				{"functionalBindings": [{"requirementRef": "req-func-1", "status": "IMPLEMENTED_LOCAL"}]}""";

		assertThat(validator.validate(result, projectId).valid()).isTrue();
		verifyNoInteractions(integrationContractResolver);
	}

	@Test
	void reportsMalformedJsonAsASingleIssue() {
		validator = new IntegrationContractBindingValidator(integrationContractResolver);
		projectId = UUID.randomUUID();

		assertThat(validator.validate("not json", projectId).valid()).isFalse();
	}
}
