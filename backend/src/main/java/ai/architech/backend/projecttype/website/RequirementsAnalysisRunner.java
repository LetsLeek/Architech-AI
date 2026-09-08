package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agent.AgentArtifactOutput;
import ai.architech.backend.core.agent.AgentDefinition;
import ai.architech.backend.core.agent.AgentDefinitionLoader;
import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.CandidateOutput;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.artifact.RequirementsOutputPersister;
import ai.architech.backend.core.artifact.RequirementsPersistenceResult;
import ai.architech.backend.core.evidence.EvidenceSnapshot;
import ai.architech.backend.core.evidence.EvidenceSnapshotFactory;
import ai.architech.backend.core.runner.BoundedRetryAgentRunner;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.ArtifactSchemaValidator;
import ai.architech.backend.core.validation.CrossArtifactValidator;
import ai.architech.backend.core.validation.CustomerProfileSemanticValidator;
import ai.architech.backend.core.validation.LocalRefUniquenessValidator;
import ai.architech.backend.core.validation.OutputContractParser;
import ai.architech.backend.core.validation.OutputContractResult;
import ai.architech.backend.core.validation.WebsiteRequirementsSemanticValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Runs Requirements Analysis for a Website Project end-to-end: takes an evidence snapshot,
 * runs the frozen Requirements Agent (steps 1-8, {@link ai.architech.backend.core.runner.AgentRunner}),
 * then performs the remaining Runner lifecycle steps 9-11 from
 * docs/core/RUNNER_VALIDATION_CONTRACT.md - the Validation Pipeline, atomic canonical
 * persistence, and final outcome recording - that {@code AgentRunner} deliberately stops
 * short of.
 *
 * <p>This class, not {@code AgentRunner}, is where that validation logic belongs: the
 * Runner's own contract says it "must not contain Website-specific price, opening-hours, or
 * requirement-classification validation logic", and the customer-profile/website-requirements
 * schemas and semantic validators are exactly that. Lives under {@code projecttype.website}
 * for the same reason.
 *
 * <p>The one required deterministic layer this intentionally folds together with another:
 * Source Reference Validation (layer 3) and Cross-Artifact Validation (layer 6) both reduce,
 * for the Requirements Agent's exactly-two-required-artifacts shape, to "every sourceRef
 * either candidate cites resolves in the shared evidence snapshot" -
 * {@link CrossArtifactValidator} already does exactly that per-artifact-and-combined, so
 * running a separate per-artifact SourceRefValidator pass on top would only produce duplicate
 * issues under a different category label.
 *
 * <p>Known gap, called out rather than silently worked around: {@link BoundedRetryAgentRunner}
 * only retries on Gateway/resolution failures ({@code AgentRunnerException}), not on a
 * validation failure surfacing here - matching that class's own documented gap and this
 * ticket's acceptance criteria, which describe a validation failure as reported and
 * non-canonical, not as retried. Feeding validation issues back into a new attempt is future
 * work, not part of AIW-51.
 */
@Component
public class RequirementsAnalysisRunner {

	static final String AGENT_ID = "requirements-agent";
	static final int AGENT_VERSION = 1;

	private static final String SCHEMA_CLASSPATH_PREFIX = "classpath:project-types/website/schemas/";

	private final EvidenceSnapshotFactory evidenceSnapshotFactory;
	private final BoundedRetryAgentRunner boundedRetryAgentRunner;
	private final AgentDefinitionLoader agentDefinitionLoader;
	private final OutputContractParser outputContractParser;
	private final ArtifactSchemaValidator artifactSchemaValidator;
	private final LocalRefUniquenessValidator localRefUniquenessValidator;
	private final CustomerProfileSemanticValidator customerProfileSemanticValidator;
	private final WebsiteRequirementsSemanticValidator websiteRequirementsSemanticValidator;
	private final CrossArtifactValidator crossArtifactValidator;
	private final CandidateOutputRepository candidateOutputRepository;
	private final RequirementsOutputPersister requirementsOutputPersister;
	private final AgentExecutionRepository agentExecutionRepository;
	private final ResourceLoader resourceLoader;

	RequirementsAnalysisRunner(
			EvidenceSnapshotFactory evidenceSnapshotFactory,
			BoundedRetryAgentRunner boundedRetryAgentRunner,
			AgentDefinitionLoader agentDefinitionLoader,
			OutputContractParser outputContractParser,
			ArtifactSchemaValidator artifactSchemaValidator,
			LocalRefUniquenessValidator localRefUniquenessValidator,
			CustomerProfileSemanticValidator customerProfileSemanticValidator,
			WebsiteRequirementsSemanticValidator websiteRequirementsSemanticValidator,
			CrossArtifactValidator crossArtifactValidator,
			CandidateOutputRepository candidateOutputRepository,
			RequirementsOutputPersister requirementsOutputPersister,
			AgentExecutionRepository agentExecutionRepository,
			ResourceLoader resourceLoader) {
		this.evidenceSnapshotFactory = evidenceSnapshotFactory;
		this.boundedRetryAgentRunner = boundedRetryAgentRunner;
		this.agentDefinitionLoader = agentDefinitionLoader;
		this.outputContractParser = outputContractParser;
		this.artifactSchemaValidator = artifactSchemaValidator;
		this.localRefUniquenessValidator = localRefUniquenessValidator;
		this.customerProfileSemanticValidator = customerProfileSemanticValidator;
		this.websiteRequirementsSemanticValidator = websiteRequirementsSemanticValidator;
		this.crossArtifactValidator = crossArtifactValidator;
		this.candidateOutputRepository = candidateOutputRepository;
		this.requirementsOutputPersister = requirementsOutputPersister;
		this.agentExecutionRepository = agentExecutionRepository;
		this.resourceLoader = resourceLoader;
	}

	/** Creates the immutable evidence snapshot, runs the agent, then validates and persists. */
	public RequirementsAnalysisResult run(UUID projectId) {
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(projectId);
		RunnerResult runnerResult = boundedRetryAgentRunner.runWithRetries(snapshot.getId(), AGENT_ID, AGENT_VERSION);
		return validateAndPersist(snapshot.getId(), runnerResult);
	}

	/**
	 * The validation-and-persistence half of the lifecycle, exposed separately so it is
	 * testable against a hand-built candidate without needing a real AI provider - the
	 * platform's only registered provider today is the mock one, which always returns empty
	 * content and so can never itself produce a validation-passing candidate.
	 */
	public RequirementsAnalysisResult validateAndPersist(UUID evidenceSnapshotId, RunnerResult runnerResult) {
		AgentExecution execution = runnerResult.execution();
		AgentDefinition agentDefinition = agentDefinitionLoader.resolve(AGENT_ID, AGENT_VERSION);
		Set<String> requiredTypes = agentDefinition.outputs().artifacts().stream()
				.filter(AgentArtifactOutput::required)
				.map(AgentArtifactOutput::type)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		List<String> issues = new ArrayList<>();

		OutputContractResult contractResult = outputContractParser.parse(runnerResult.candidateOutput(), requiredTypes);
		contractResult.issues().forEach(issue -> issues.add("output-contract: " + issue));

		if (!contractResult.valid()) {
			return failExecution(execution, issues);
		}

		Map<String, CandidateOutput> candidatesByType = new LinkedHashMap<>();
		for (AgentArtifactOutput artifactOutput : agentDefinition.outputs().artifacts()) {
			String content = contractResult.artifactContentByType().get(artifactOutput.type());
			if (content == null) {
				continue;
			}
			candidatesByType.put(
					artifactOutput.type(),
					candidateOutputRepository.saveAndFlush(
							new CandidateOutput(execution.getId(), artifactOutput.type(), content)));
			issues.addAll(validateArtifact(artifactOutput, content));
		}

		String customerProfileJson =
				contractResult.artifactContentByType().get(RequirementsOutputPersister.CUSTOMER_PROFILE_TYPE);
		String websiteRequirementsJson =
				contractResult.artifactContentByType().get(RequirementsOutputPersister.WEBSITE_REQUIREMENTS_TYPE);

		if (customerProfileJson != null && websiteRequirementsJson != null) {
			crossArtifactValidator.validate(evidenceSnapshotId, customerProfileJson, websiteRequirementsJson).issues()
					.forEach(issue -> issues.add("cross-artifact[" + issue.artifactType() + "] " + issue.ref() + ": " + issue.reason()));
		}

		boolean allArtifactsValid = issues.isEmpty();
		CandidateOutput customerProfileCandidate = candidatesByType.get(RequirementsOutputPersister.CUSTOMER_PROFILE_TYPE);
		CandidateOutput websiteRequirementsCandidate =
				candidatesByType.get(RequirementsOutputPersister.WEBSITE_REQUIREMENTS_TYPE);

		RequirementsPersistenceResult persistence = (customerProfileCandidate != null && websiteRequirementsCandidate != null)
				? requirementsOutputPersister.persist(
						execution.getProjectId(),
						customerProfileCandidate,
						allArtifactsValid,
						websiteRequirementsCandidate,
						allArtifactsValid)
				: RequirementsPersistenceResult.rejected();

		if (persistence.persisted()) {
			execution.succeed();
		} else {
			execution.fail(issues.isEmpty() ? "one or both required candidate artifacts are missing" : String.join("; ", issues));
		}
		agentExecutionRepository.save(execution);

		return new RequirementsAnalysisResult(execution, persistence, issues);
	}

	private List<String> validateArtifact(AgentArtifactOutput artifactOutput, String content) {
		String type = artifactOutput.type();
		List<String> artifactIssues = new ArrayList<>();

		artifactSchemaValidator.validate(resolveSchema(artifactOutput), content).issues()
				.forEach(issue -> artifactIssues.add("schema[" + type + "] " + issue.path() + ": " + issue.message()));

		localRefUniquenessValidator.validate(content).issues()
				.forEach(issue -> artifactIssues.add("local-ref[" + type + "] " + issue.localRef() + ": " + issue.reason()));

		if (RequirementsOutputPersister.CUSTOMER_PROFILE_TYPE.equals(type)) {
			customerProfileSemanticValidator.validate(content).issues()
					.forEach(issue -> artifactIssues.add("semantic[" + type + "] " + issue.location() + ": " + issue.reason()));
		} else if (RequirementsOutputPersister.WEBSITE_REQUIREMENTS_TYPE.equals(type)) {
			websiteRequirementsSemanticValidator.validate(content).issues()
					.forEach(issue -> artifactIssues.add("semantic[" + type + "] " + issue.location() + ": " + issue.reason()));
		}

		return artifactIssues;
	}

	/**
	 * {@code AgentArtifactOutput.schema()} is the raw relative path string from agent.yaml
	 * (e.g. "../../schemas/customer-profile.schema.json") - the {@code Resource} that would
	 * let us resolve it relatively isn't retained on the parsed {@link AgentDefinition}. Both
	 * frozen schemas live at the one well-known location the Maven build copies
	 * {@code project-types/} to on the classpath, so resolving by filename there is simpler
	 * and just as correct as re-deriving the relative resolution.
	 */
	private Resource resolveSchema(AgentArtifactOutput artifactOutput) {
		String schemaPath = artifactOutput.schema();
		String filename = schemaPath.substring(schemaPath.lastIndexOf('/') + 1);
		return resourceLoader.getResource(SCHEMA_CLASSPATH_PREFIX + filename);
	}

	private RequirementsAnalysisResult failExecution(AgentExecution execution, List<String> issues) {
		execution.fail(String.join("; ", issues));
		agentExecutionRepository.save(execution);
		return new RequirementsAnalysisResult(execution, RequirementsPersistenceResult.rejected(), issues);
	}
}
