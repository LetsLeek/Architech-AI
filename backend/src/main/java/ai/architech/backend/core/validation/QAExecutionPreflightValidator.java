package ai.architech.backend.core.validation;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import ai.architech.backend.core.verification.VerificationOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Deterministic preflight validation for a {@code qa-execution-input:v1} payload, run before any
 * semantic QA Agent invocation (AIW-169) - the QA analogue of {@link
 * DeveloperExecutionInputValidator}, same "invalid pre-execution state prevents model invocation;
 * it is a Core/validation failure" boundary.
 *
 * <p><b>What this class validates</b>: strict schema validation ({@link WebsiteQaSchemaRegistry});
 * that {@code target.candidateRef} resolves to a real, persisted {@link
 * WebsiteImplementationCandidate} belonging to the calling project (project/customer isolation);
 * that the referenced Candidate's owning {@code AgentExecution} has at least one {@code PASS}
 * {@link RunnerVerificationRun} whose {@code repositoryStateRef} matches the Candidate's own
 * (Technical Verification PASS provenance - {@code rules/target-input-integrity.md}'s own
 * requirement); that {@code productAuthority.sourceDesignRef}/{@code runtimeProfileRef} match the
 * Candidate's actual bindings exactly ({@link WebsiteImplementationCandidate#getSourceDesignRef()}
 * /{@link WebsiteImplementationCandidate#getRuntimeProfileRef()}); that every {@code qaAuthority}/
 * {@code executionContext.toolCapabilityProfileRef} ref matches this V1 epic's one frozen value
 * for each (there is only one version of each in V1, the same "frozen V1 profiles' own stable
 * ids" idiom {@code DeveloperExecutionInputAssembler}'s own constants already establish).
 *
 * <p><b>What this class deliberately does not validate yet</b>: whether {@code
 * executionContext.executionSurfaceRef} actually points at a live, Candidate-bound Preview
 * surface - no durable, re-visitable Preview provisioning infrastructure exists anywhere in this
 * codebase yet (a real architectural gap discovered while building this ticket: {@code
 * WebsiteImplementationCandidate.repositoryStateRef} is a content digest, not a filesystem/remote
 * location, so nothing today can locate an accepted Candidate's actual source tree after the
 * Developer execution that produced it has finished). {@link CandidateBindingValidator} covers
 * the "wrong Candidate was actually observed" / "execution-surface drift" classification this
 * ticket's own AC also names, but only given an already-observed surface identity as input -
 * producing that observation for real is future, currently-unticketed work, the same class of gap
 * AIW-184 already documents for the Developer Agent's own tool-calling loop.
 */
@Component
public class QAExecutionPreflightValidator {

	static final String INPUT_SCHEMA_URN = "urn:aiw:schema:qa-execution-input:v1";

	// V1's frozen, single-version package-level refs - matching the exact strings the frozen QA
	// package's own fixtures (project-types/website/agents/website-qa-agent/fixtures/) use.
	static final Set<String> VALID_QA_PROFILE_REFS =
			Set.of("website-qa-comparison-readiness@1.0.0", "website-qa-full-release@1.0.0");
	static final String EXPECTED_RULE_SET_REF = "website-qa-rules@1.0.0";
	static final String EXPECTED_SKILL_SET_REF = "website-qa-skills@1.0.0";
	static final String EXPECTED_VALIDATOR_SET_REF = "website-qa-validator-set@1.0.0";
	static final String EXPECTED_FINDING_TAXONOMY_REF = "website-qa-finding-taxonomy@1.0.0";
	static final String EXPECTED_CHECK_REGISTRY_REF = "website-qa-check-registry@1.0.0";
	static final String EXPECTED_TOOL_CAPABILITY_PROFILE_REF = "website-qa-tools@1.0.0";

	private final WebsiteQaSchemaRegistry schemaRegistry;
	private final WebsiteImplementationCandidateRepository candidateRepository;
	private final RunnerVerificationRunRepository runnerVerificationRunRepository;
	private final ObjectMapper objectMapper;

	QAExecutionPreflightValidator(
			WebsiteQaSchemaRegistry schemaRegistry,
			WebsiteImplementationCandidateRepository candidateRepository,
			RunnerVerificationRunRepository runnerVerificationRunRepository,
			ObjectMapper objectMapper) {
		this.schemaRegistry = schemaRegistry;
		this.candidateRepository = candidateRepository;
		this.runnerVerificationRunRepository = runnerVerificationRunRepository;
		this.objectMapper = objectMapper;
	}

	public PreExecutionValidationResult validate(UUID projectId, String qaExecutionInputJson) {
		SchemaValidationResult schemaResult = schemaRegistry.validate(INPUT_SCHEMA_URN, qaExecutionInputJson);
		if (!schemaResult.valid()) {
			return new PreExecutionValidationResult(
					schemaResult.issues().stream().map(i -> new PreExecutionValidationIssue(i.path(), i.message())).toList());
		}

		JsonNode input = objectMapper.readTree(qaExecutionInputJson);
		List<PreExecutionValidationIssue> issues = new ArrayList<>();

		resolveCandidate(projectId, input, issues).ifPresent(candidate -> {
			validateTechnicalVerificationPass(candidate, issues);
			validateProductAuthorityBinding(input, candidate, issues);
		});
		validateQaAuthorityRefs(input, issues);
		validateToolCapabilityProfileRef(input, issues);

		return new PreExecutionValidationResult(issues);
	}

	private Optional<WebsiteImplementationCandidate> resolveCandidate(
			UUID projectId, JsonNode input, List<PreExecutionValidationIssue> issues) {
		String candidateRefPath = "target/candidateRef";
		String ref = input.path("target").path("candidateRef").asString();

		UUID candidateId;
		try {
			candidateId = UUID.fromString(ref);
		} catch (IllegalArgumentException e) {
			issues.add(new PreExecutionValidationIssue(candidateRefPath, "not a resolvable Candidate reference: '" + ref + "'"));
			return Optional.empty();
		}

		Optional<WebsiteImplementationCandidate> candidateOpt = candidateRepository.findById(candidateId);
		if (candidateOpt.isEmpty()) {
			issues.add(new PreExecutionValidationIssue(candidateRefPath, "no WebsiteImplementationCandidate exists for reference '" + ref + "'"));
			return Optional.empty();
		}

		WebsiteImplementationCandidate candidate = candidateOpt.get();
		if (!candidate.getProjectId().equals(projectId)) {
			issues.add(new PreExecutionValidationIssue(
					candidateRefPath, "Candidate '" + ref + "' does not belong to the calling project"));
			return Optional.empty();
		}

		return Optional.of(candidate);
	}

	private void validateTechnicalVerificationPass(WebsiteImplementationCandidate candidate, List<PreExecutionValidationIssue> issues) {
		List<RunnerVerificationRun> runs =
				runnerVerificationRunRepository.findByAgentExecutionIdOrderByCreatedAtAsc(candidate.getAgentExecutionId());
		boolean hasMatchingPass = runs.stream()
				.anyMatch(run -> run.getOutcome() == VerificationOutcome.PASS
						&& run.getRepositoryStateRef().equals(candidate.getRepositoryStateRef()));
		if (!hasMatchingPass) {
			issues.add(new PreExecutionValidationIssue(
					"target/candidateRef",
					"no PASS Runner Verification exists for Candidate '" + candidate.getId()
							+ "' against its exact repository state '" + candidate.getRepositoryStateRef() + "'"));
		}
	}

	private void validateProductAuthorityBinding(
			JsonNode input, WebsiteImplementationCandidate candidate, List<PreExecutionValidationIssue> issues) {
		JsonNode productAuthority = input.path("productAuthority");
		String claimedSourceDesignRef = productAuthority.path("sourceDesignRef").asString();
		if (!candidate.getSourceDesignRef().equals(claimedSourceDesignRef)) {
			issues.add(new PreExecutionValidationIssue(
					"productAuthority/sourceDesignRef",
					"does not match the Candidate's own source design binding '" + candidate.getSourceDesignRef() + "'"));
		}

		String claimedRuntimeProfileRef = productAuthority.path("runtimeProfileRef").asString();
		if (!candidate.getRuntimeProfileRef().equals(claimedRuntimeProfileRef)) {
			issues.add(new PreExecutionValidationIssue(
					"productAuthority/runtimeProfileRef",
					"does not match the Candidate's own runtime profile binding '" + candidate.getRuntimeProfileRef() + "'"));
		}
	}

	private void validateQaAuthorityRefs(JsonNode input, List<PreExecutionValidationIssue> issues) {
		JsonNode qaAuthority = input.path("qaAuthority");
		requireExactMatch(qaAuthority, "qaProfileRef", VALID_QA_PROFILE_REFS, "qaAuthority/qaProfileRef", issues);
		requireExactMatch(qaAuthority, "ruleSetRef", Set.of(EXPECTED_RULE_SET_REF), "qaAuthority/ruleSetRef", issues);
		requireExactMatch(qaAuthority, "skillSetRef", Set.of(EXPECTED_SKILL_SET_REF), "qaAuthority/skillSetRef", issues);
		requireExactMatch(qaAuthority, "validatorSetRef", Set.of(EXPECTED_VALIDATOR_SET_REF), "qaAuthority/validatorSetRef", issues);
		requireExactMatch(
				qaAuthority, "findingTaxonomyRef", Set.of(EXPECTED_FINDING_TAXONOMY_REF), "qaAuthority/findingTaxonomyRef", issues);
		requireExactMatch(qaAuthority, "checkRegistryRef", Set.of(EXPECTED_CHECK_REGISTRY_REF), "qaAuthority/checkRegistryRef", issues);
	}

	private void validateToolCapabilityProfileRef(JsonNode input, List<PreExecutionValidationIssue> issues) {
		requireExactMatch(
				input.path("executionContext"),
				"toolCapabilityProfileRef",
				Set.of(EXPECTED_TOOL_CAPABILITY_PROFILE_REF),
				"executionContext/toolCapabilityProfileRef",
				issues);
	}

	private void requireExactMatch(
			JsonNode parent, String field, Set<String> allowed, String path, List<PreExecutionValidationIssue> issues) {
		String actual = parent.path(field).asString();
		if (!allowed.contains(actual)) {
			issues.add(new PreExecutionValidationIssue(path, "unknown or unsupported value '" + actual + "' - expected one of " + allowed));
		}
	}
}
