package ai.architech.backend.core.validation;

import ai.architech.backend.core.toolexecution.ToolExecution;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The complete {@code DeveloperAgentResult} validation pipeline (AIW-142) run after final
 * Developer handoff and before authoritative Runner Verification, per {@code
 * docs/VALIDATION-PIPELINE.md}: strict discriminated-union schema validation first (a schema
 * failure short-circuits everything below it, since nothing else can be meaningfully evaluated
 * against a candidate that doesn't even match its own declared shape), then target-identity,
 * then the branch-specific validators - {@code IMPLEMENTATION_READY} gets reference, anchor,
 * functional-binding, integration-contract and unresolved-issue validation; {@code BLOCKED} gets
 * reference and blocker-evidence validation. Validators only ever detect and report; none of them
 * repair, normalize or drop an offending field from the candidate.
 *
 * <p>There is no {@code DeveloperAgentRunner} yet to wire this into (the tool-dispatch loop and
 * sandbox execution this epic still needs have no owning ticket - see the M3 sequencing plan's
 * own "Track 2C" gap) - this class is this ticket's own testable pipeline entry point, exactly
 * the boundary AIW-142's acceptance criteria describes, ready for whatever eventually orchestrates
 * a live Developer execution to call.
 */
@Component
public class DeveloperResultValidator {

	private final DeveloperSchemaRegistry developerSchemaRegistry;
	private final DeveloperResultTargetIdentityValidator targetIdentityValidator;
	private final DeveloperResultReferenceValidator referenceValidator;
	private final ImplementationAnchorValidator implementationAnchorValidator;
	private final FunctionalBindingValidator functionalBindingValidator;
	private final IntegrationContractBindingValidator integrationContractBindingValidator;
	private final UnresolvedIssueValidator unresolvedIssueValidator;
	private final DeveloperBlockerValidator developerBlockerValidator;
	private final ObjectMapper objectMapper = new ObjectMapper();

	DeveloperResultValidator(
			DeveloperSchemaRegistry developerSchemaRegistry,
			DeveloperResultTargetIdentityValidator targetIdentityValidator,
			DeveloperResultReferenceValidator referenceValidator,
			ImplementationAnchorValidator implementationAnchorValidator,
			FunctionalBindingValidator functionalBindingValidator,
			IntegrationContractBindingValidator integrationContractBindingValidator,
			UnresolvedIssueValidator unresolvedIssueValidator,
			DeveloperBlockerValidator developerBlockerValidator) {
		this.developerSchemaRegistry = developerSchemaRegistry;
		this.targetIdentityValidator = targetIdentityValidator;
		this.referenceValidator = referenceValidator;
		this.implementationAnchorValidator = implementationAnchorValidator;
		this.functionalBindingValidator = functionalBindingValidator;
		this.integrationContractBindingValidator = integrationContractBindingValidator;
		this.unresolvedIssueValidator = unresolvedIssueValidator;
		this.developerBlockerValidator = developerBlockerValidator;
	}

	public DeveloperResultValidationResult validate(
			String resultJson,
			String executionInputJson,
			UUID projectId,
			Set<String> repositoryFiles,
			List<ToolExecution> toolExecutions) {
		List<DeveloperResultValidationIssue> issues = new ArrayList<>();

		developerSchemaRegistry
				.validate("urn:aiw:schema:developer-agent-result:v1", resultJson)
				.issues()
				.forEach(issue -> issues.add(new DeveloperResultValidationIssue("schema", issue.path(), issue.message())));
		if (!issues.isEmpty()) {
			return new DeveloperResultValidationResult(false, issues);
		}

		JsonNode result = objectMapper.readTree(resultJson);
		JsonNode executionInput = objectMapper.readTree(executionInputJson);
		String proposalJson = objectMapper.writeValueAsString(executionInput.path("targetDesign").path("proposal"));
		String websiteRequirementsJson =
				objectMapper.writeValueAsString(executionInput.path("canonicalUpstream").path("websiteRequirements"));

		issues.addAll(targetIdentityValidator.validate(resultJson, executionInputJson).issues());

		String resultType = result.path("resultType").asString(null);
		if ("IMPLEMENTATION_READY".equals(resultType)) {
			issues.addAll(referenceValidator.validate(resultJson, websiteRequirementsJson, proposalJson).issues());
			issues.addAll(implementationAnchorValidator.validate(resultJson, proposalJson, repositoryFiles).issues());
			issues.addAll(functionalBindingValidator.validate(resultJson, websiteRequirementsJson, proposalJson).issues());
			issues.addAll(integrationContractBindingValidator.validate(resultJson, projectId).issues());
			issues.addAll(unresolvedIssueValidator.validate(resultJson).issues());
		} else if ("BLOCKED".equals(resultType)) {
			issues.addAll(referenceValidator.validate(resultJson, websiteRequirementsJson, proposalJson).issues());
			issues.addAll(developerBlockerValidator.validate(resultJson, toolExecutions).issues());
		}

		return issues.isEmpty() ? DeveloperResultValidationResult.passed() : new DeveloperResultValidationResult(false, issues);
	}
}
