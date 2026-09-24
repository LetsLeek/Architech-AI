package ai.architech.backend.core.validation;

import ai.architech.backend.core.integration.IntegrationContractResolution;
import ai.architech.backend.core.integration.IntegrationContractResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks every {@code IMPLEMENTED_BOUND} {@code functionalBinding}'s {@code
 * integrationContractRef} resolves to a genuinely authorized, schema-valid Integration Contract
 * for this project (AIW-142's "Integration Contract compatibility checks for
 * IMPLEMENTED_BOUND"), via AIW-144's {@link IntegrationContractResolver} - never by re-deciding
 * authorization from the candidate's own claims. {@code UNBOUND} bindings already carry their own
 * {@code blockerCode} ({@code MISSING_INTEGRATION_CONTRACT}/{@code INVALID_INTEGRATION_CONTRACT})
 * enforced structurally by the {@code functional-binding:v1} schema itself, so this validator only
 * needs to police the branch the schema cannot: a binding that *claims* to be bound.
 */
@Component
public class IntegrationContractBindingValidator {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final IntegrationContractResolver integrationContractResolver;

	IntegrationContractBindingValidator(IntegrationContractResolver integrationContractResolver) {
		this.integrationContractResolver = integrationContractResolver;
	}

	public DeveloperResultValidationResult validate(String resultJson, UUID projectId) {
		JsonNode result;
		try {
			result = objectMapper.readTree(resultJson);
		} catch (RuntimeException e) {
			return new DeveloperResultValidationResult(
					false,
					List.of(new DeveloperResultValidationIssue("integration-contract", "$", "candidate is not valid JSON: " + e.getMessage())));
		}

		List<DeveloperResultValidationIssue> issues = new ArrayList<>();
		for (JsonNode binding : result.path("functionalBindings")) {
			if (!"IMPLEMENTED_BOUND".equals(binding.path("status").asString(null))) {
				continue;
			}
			String contractRef = binding.path("integrationContractRef").asString(null);
			IntegrationContractResolution resolution = integrationContractResolver.resolve(projectId, contractRef);
			switch (resolution) {
				case IntegrationContractResolution.Missing ignored -> issues.add(new DeveloperResultValidationIssue(
						"integration-contract",
						"functionalBindings.integrationContractRef",
						"'" + contractRef + "' is not an authorized Integration Contract for this project (MISSING_INTEGRATION_CONTRACT)"));
				case IntegrationContractResolution.Invalid invalid -> issues.add(new DeveloperResultValidationIssue(
						"integration-contract",
						"functionalBindings.integrationContractRef",
						"'" + contractRef + "' failed its own safe-contract schema (INVALID_INTEGRATION_CONTRACT): " + invalid.validationError()));
				case IntegrationContractResolution.Authorized ignored -> {
					// bound correctly - nothing to report
				}
			}
		}

		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}
}
