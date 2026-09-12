package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agent.AgentArtifactOutput;
import ai.architech.backend.core.agent.AgentDefinition;
import ai.architech.backend.core.agent.AgentDefinitionLoader;
import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.artifact.CandidateOutput;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.artifact.CandidatePromoter;
import ai.architech.backend.core.error.ApplicationException;
import ai.architech.backend.core.error.ErrorCode;
import ai.architech.backend.core.runner.BoundedRetryAgentRunner;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.ArtifactSchemaValidator;
import ai.architech.backend.core.validation.DesignProposalSetCanonicalReferenceValidator;
import ai.architech.backend.core.validation.DesignProposalSetStructureValidator;
import ai.architech.backend.core.validation.DesignProposalSetSemanticReviewer;
import ai.architech.backend.core.validation.LocalRefUniquenessValidator;
import ai.architech.backend.core.validation.OutputContractParser;
import ai.architech.backend.core.validation.OutputContractResult;
import ai.architech.backend.core.validation.SemanticReviewFinding;
import ai.architech.backend.core.validation.SemanticReviewResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the Designer Agent for a Website Project end-to-end (AIW-124): fetches the project's
 * canonical {@code customer-profile}/{@code website-requirements} artifacts (never a raw
 * Source Context - see {@code DESIGNER_AGENT_V1_FREEZE.md}), runs the frozen Designer Agent via
 * {@link ai.architech.backend.core.runner.AgentRunner#runWithInputArtifacts}, then performs the
 * Runner lifecycle's remaining steps: the layered Validation Pipeline and atomic canonical
 * persistence - mirroring {@code RequirementsAnalysisRunner}'s own structure and reasoning for
 * why this Website-specific validation logic belongs here, not in the generic {@code
 * AgentRunner}.
 *
 * <p>Validation order is fixed per {@code docs/core/RUNNER_VALIDATION_CONTRACT.md}: output
 * contract, then JSON Schema, then the deterministic design validators (AIW-122), then semantic
 * review (AIW-123) - and only if every earlier stage already passed, since a real AI review call
 * has a real cost this project's own cost-conscious testing/operating practice avoids spending
 * on a candidate already known to be invalid.
 *
 * <p>Only one required output artifact ({@code design-proposal-set}), unlike the Requirements
 * Agent's two - so a plain {@link CandidatePromoter#promote} is this ticket's own atomic
 * persistence unit; no {@code RequirementsOutputPersister}-style dual-artifact transactional
 * wrapper is needed for a single promotion.
 */
@Component
public class DesignerAgentRunner {

	private static final Logger log = LoggerFactory.getLogger(DesignerAgentRunner.class);

	static final String AGENT_ID = "designer-agent";
	static final int AGENT_VERSION = 1;
	static final String DESIGN_PROPOSAL_SET_TYPE = "design-proposal-set";
	static final String CUSTOMER_PROFILE_TYPE = "customer-profile";
	static final String WEBSITE_REQUIREMENTS_TYPE = "website-requirements";

	private final ArtifactRepository artifactRepository;
	private final ArtifactVersionRepository artifactVersionRepository;
	private final BoundedRetryAgentRunner boundedRetryAgentRunner;
	private final AgentDefinitionLoader agentDefinitionLoader;
	private final OutputContractParser outputContractParser;
	private final ArtifactSchemaValidator artifactSchemaValidator;
	private final LocalRefUniquenessValidator localRefUniquenessValidator;
	private final DesignProposalSetStructureValidator designProposalSetStructureValidator;
	private final DesignProposalSetCanonicalReferenceValidator designProposalSetCanonicalReferenceValidator;
	private final DesignProposalSetSemanticReviewer designProposalSetSemanticReviewer;
	private final CandidateOutputRepository candidateOutputRepository;
	private final CandidatePromoter candidatePromoter;
	private final AgentExecutionRepository agentExecutionRepository;

	DesignerAgentRunner(
			ArtifactRepository artifactRepository,
			ArtifactVersionRepository artifactVersionRepository,
			BoundedRetryAgentRunner boundedRetryAgentRunner,
			AgentDefinitionLoader agentDefinitionLoader,
			OutputContractParser outputContractParser,
			ArtifactSchemaValidator artifactSchemaValidator,
			LocalRefUniquenessValidator localRefUniquenessValidator,
			DesignProposalSetStructureValidator designProposalSetStructureValidator,
			DesignProposalSetCanonicalReferenceValidator designProposalSetCanonicalReferenceValidator,
			DesignProposalSetSemanticReviewer designProposalSetSemanticReviewer,
			CandidateOutputRepository candidateOutputRepository,
			CandidatePromoter candidatePromoter,
			AgentExecutionRepository agentExecutionRepository) {
		this.artifactRepository = artifactRepository;
		this.artifactVersionRepository = artifactVersionRepository;
		this.boundedRetryAgentRunner = boundedRetryAgentRunner;
		this.agentDefinitionLoader = agentDefinitionLoader;
		this.outputContractParser = outputContractParser;
		this.artifactSchemaValidator = artifactSchemaValidator;
		this.localRefUniquenessValidator = localRefUniquenessValidator;
		this.designProposalSetStructureValidator = designProposalSetStructureValidator;
		this.designProposalSetCanonicalReferenceValidator = designProposalSetCanonicalReferenceValidator;
		this.designProposalSetSemanticReviewer = designProposalSetSemanticReviewer;
		this.candidateOutputRepository = candidateOutputRepository;
		this.candidatePromoter = candidatePromoter;
		this.agentExecutionRepository = agentExecutionRepository;
	}

	/**
	 * Fetches the project's canonical required input artifacts, runs the agent, then validates
	 * and persists. Throws {@link ApplicationException} with {@link
	 * ErrorCode#CANONICAL_ARTIFACT_NOT_FOUND} if either required canonical input doesn't exist
	 * yet for this project - the Designer Agent has a real, checkable precondition (the
	 * Requirements Agent must have already succeeded for this project) that the Requirements
	 * Agent itself, reading only raw Source Context, never had.
	 */
	public DesignProposalGenerationResult run(UUID projectId) {
		String customerProfileJson = fetchCanonicalArtifact(projectId, CUSTOMER_PROFILE_TYPE);
		String websiteRequirementsJson = fetchCanonicalArtifact(projectId, WEBSITE_REQUIREMENTS_TYPE);

		RunnerResult runnerResult = boundedRetryAgentRunner.runWithRetriesUsingInputArtifacts(
				projectId,
				AGENT_ID,
				AGENT_VERSION,
				Map.of(CUSTOMER_PROFILE_TYPE, customerProfileJson, WEBSITE_REQUIREMENTS_TYPE, websiteRequirementsJson));

		return validateAndPersist(projectId, customerProfileJson, websiteRequirementsJson, runnerResult);
	}

	/**
	 * The validation-and-persistence half of the lifecycle, exposed separately so it is testable
	 * against a hand-built candidate without needing a real AI provider - same reasoning as
	 * {@code RequirementsAnalysisRunner#validateAndPersist}.
	 */
	public DesignProposalGenerationResult validateAndPersist(
			UUID projectId, String customerProfileJson, String websiteRequirementsJson, RunnerResult runnerResult) {
		AgentExecution execution = runnerResult.execution();
		AgentDefinition agentDefinition = agentDefinitionLoader.resolve(AGENT_ID, AGENT_VERSION);
		AgentArtifactOutput outputContract = agentDefinition.outputs().artifacts().stream()
				.filter(output -> DESIGN_PROPOSAL_SET_TYPE.equals(output.type()))
				.findFirst()
				.orElseThrow();

		List<String> issues = new ArrayList<>();

		OutputContractResult contractResult =
				outputContractParser.parse(runnerResult.candidateOutput(), Set.of(DESIGN_PROPOSAL_SET_TYPE));
		contractResult.issues().forEach(issue -> issues.add("output-contract: " + issue));

		if (!contractResult.valid()) {
			// Same reasoning as RequirementsAnalysisRunner: the raw candidate is otherwise lost
			// forever the moment this returns - nothing else persists it when output-contract
			// validation itself fails.
			log.warn(
					"Designer Agent output failed output-contract validation for execution {}: {}",
					execution.getId(),
					runnerResult.candidateOutput());
			return failExecution(execution, issues);
		}

		String candidateJson = contractResult.artifactContentByType().get(DESIGN_PROPOSAL_SET_TYPE);
		CandidateOutput candidate = candidateOutputRepository.saveAndFlush(
				new CandidateOutput(execution.getId(), DESIGN_PROPOSAL_SET_TYPE, candidateJson));

		artifactSchemaValidator.validate(outputContract.schemaContent(), candidateJson).issues()
				.forEach(issue -> issues.add("schema: " + issue.path() + ": " + issue.message()));
		localRefUniquenessValidator.validate(candidateJson).issues()
				.forEach(issue -> issues.add("local-ref: " + issue.localRef() + ": " + issue.reason()));
		designProposalSetStructureValidator.validate(candidateJson).issues()
				.forEach(issue -> issues.add("structure[" + issue.proposalRef() + "] " + issue.path() + ": " + issue.reason()));
		designProposalSetCanonicalReferenceValidator
				.validate(candidateJson, customerProfileJson, websiteRequirementsJson)
				.issues()
				.forEach(issue -> issues.add("canonical-ref[" + issue.proposalRef() + "] " + issue.path() + ": " + issue.reason()));

		// Semantic review has a real cost - only run it once every earlier, cheaper stage
		// already passed (RUNNER_VALIDATION_CONTRACT.md's own layered pipeline order).
		if (issues.isEmpty()) {
			SemanticReviewResult semanticReview = designProposalSetSemanticReviewer.review(
					candidateJson, customerProfileJson, websiteRequirementsJson, execution.getId().toString());
			for (SemanticReviewFinding finding : semanticReview.findings()) {
				if (finding.blocking()) {
					issues.add("semantic-review[" + finding.proposalRef() + "] " + finding.category() + ": " + finding.message());
				}
			}
		}

		ArtifactVersion version = null;
		if (issues.isEmpty()) {
			version = candidatePromoter.promote(projectId, candidate);
			execution.succeed();
		} else {
			execution.fail(String.join("; ", issues));
		}
		agentExecutionRepository.save(execution);

		return new DesignProposalGenerationResult(execution, version, issues);
	}

	private String fetchCanonicalArtifact(UUID projectId, String type) {
		Optional<String> content = artifactRepository
				.findByProjectIdAndType(projectId, type)
				.flatMap(this::latestVersionContent);
		return content.orElseThrow(() -> new ApplicationException(
				ErrorCode.CANONICAL_ARTIFACT_NOT_FOUND,
				"No canonical '" + type + "' artifact exists yet for project " + projectId));
	}

	private Optional<String> latestVersionContent(Artifact artifact) {
		return artifactVersionRepository
				.findTopByArtifactIdOrderByVersionNumberDesc(artifact.getId())
				.map(ArtifactVersion::getContent);
	}

	private DesignProposalGenerationResult failExecution(AgentExecution execution, List<String> issues) {
		execution.fail(String.join("; ", issues));
		agentExecutionRepository.save(execution);
		return new DesignProposalGenerationResult(execution, null, issues);
	}
}
