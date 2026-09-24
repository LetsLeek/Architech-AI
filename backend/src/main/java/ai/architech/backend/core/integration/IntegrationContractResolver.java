package ai.architech.backend.core.integration;

import ai.architech.backend.core.validation.DeveloperSchemaRegistry;
import ai.architech.backend.core.validation.SchemaValidationIssue;
import ai.architech.backend.core.validation.SchemaValidationResult;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves a Developer's {@code integrationContractRef} into an authorized, schema-valid
 * {@link IntegrationContract} - the boundary AIW-144's own acceptance criteria describes as
 * "Core can resolve a versioned Integration Contract into a strictly safe Developer projection"
 * and "Cross-project or unauthorized contracts are rejected before execution".
 *
 * <p>Every lookup is scoped by {@code (projectId, contractRef)} together (delegating to
 * {@link IntegrationContractRepository#findByProjectIdAndContractRef}) - there is no method here
 * that resolves a contract by ref alone, so a real contract belonging to a different project is
 * structurally unreachable regardless of how its ref string looks, exactly like
 * {@code core.asset.ProjectAssetResolver}.
 */
@Component
public class IntegrationContractResolver {

	private final IntegrationContractRepository integrationContractRepository;
	private final DeveloperSchemaRegistry developerSchemaRegistry;

	IntegrationContractResolver(
			IntegrationContractRepository integrationContractRepository, DeveloperSchemaRegistry developerSchemaRegistry) {
		this.integrationContractRepository = integrationContractRepository;
		this.developerSchemaRegistry = developerSchemaRegistry;
	}

	public IntegrationContractResolution resolve(UUID projectId, String contractRef) {
		return integrationContractRepository
				.findByProjectIdAndContractRef(projectId, contractRef)
				.<IntegrationContractResolution>map(this::validated)
				.orElseGet(IntegrationContractResolution.Missing::new);
	}

	private IntegrationContractResolution validated(IntegrationContract contract) {
		SchemaValidationResult result = developerSchemaRegistry.validate(
				"urn:aiw:schema:developer-safe-integration-contract-view:v1", contract.getSafeContractContent());
		if (result.valid()) {
			return new IntegrationContractResolution.Authorized(contract);
		}
		return new IntegrationContractResolution.Invalid(contract, summarize(result));
	}

	private String summarize(SchemaValidationResult result) {
		return result.issues().stream()
				.map(SchemaValidationIssue::message)
				.collect(Collectors.joining("; "));
	}
}
