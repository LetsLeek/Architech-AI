package ai.architech.backend.projecttype.website;

import ai.architech.backend.core.agent.AgentArtifactOutput;
import ai.architech.backend.core.agent.AgentDefinition;
import ai.architech.backend.core.agent.AgentDefinitionLoader;
import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.CandidateOutput;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.runner.BoundedRetryAgentRunner;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.ArtifactSchemaValidator;
import ai.architech.backend.core.validation.OutputContractParser;
import ai.architech.backend.core.validation.OutputContractResult;
import ai.architech.backend.core.validation.SemanticQaReviewOutputIdentityValidator;
import ai.architech.backend.core.validation.SemanticQaReviewOutputIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the Website QA Agent's semantic review step end to end (AIW-173): calls the frozen
 * {@code website-qa-agent} via the existing generic {@link
 * ai.architech.backend.core.runner.AgentRunner#runWithInputArtifacts} (no new Runner mechanism
 * needed - the QA Agent's own {@code agent.yaml} declares exactly one required input artifact,
 * {@code qa-execution-input}, the same "prior canonical artifact" shape {@code DesignerAgentRunner}
 * already uses {@code runWithInputArtifacts} for), records the {@link QaExecution}/{@link
 * QaInputSnapshot} rows this attempt is bound to, then validates the raw candidate.
 *
 * <p>Validation order mirrors {@code DesignerAgentRunner}'s own layered pipeline: output
 * contract, then JSON Schema, then result identity (does the candidate actually claim the exact
 * execution/Candidate/profile/snapshot this attempt was given). Unlike {@code
 * DesignerAgentRunner}, there is no promotion step at all - {@link CandidateOutput} is this
 * pipeline's own final resting place, matching AIW-173's own "Output is treated as
 * non-authoritative SemanticQAReviewOutput Candidate Output" requirement. Converting the
 * candidate's {@code findingCandidates}/etc. into real, persisted rows is AIW-174's scope.
 *
 * <p>Live end-to-end runs remain blocked on AIW-184 (the Developer/QA tool-calling loop) for the
 * same reason already documented there and in {@code QAExecutionPreflightValidator}'s own
 * javadoc - this class is complete and real, ready for whatever eventually calls it with a real
 * {@code AiGateway} wired to a tool-calling loop. Tests exercise it with a mocked {@code
 * AiResponse}, never a live model call.
 */
@Component
public class QaSemanticReviewOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(QaSemanticReviewOrchestrator.class);

	static final String AGENT_ID = "website-qa-agent";
	static final int AGENT_VERSION = 1;
	static final String INPUT_TYPE = "qa-execution-input";
	static final String OUTPUT_TYPE = "semantic-qa-review-output";

	private final BoundedRetryAgentRunner boundedRetryAgentRunner;
	private final AgentDefinitionLoader agentDefinitionLoader;
	private final OutputContractParser outputContractParser;
	private final ArtifactSchemaValidator artifactSchemaValidator;
	private final SemanticQaReviewOutputIdentityValidator identityValidator;
	private final CandidateOutputRepository candidateOutputRepository;
	private final QaExecutionRepository qaExecutionRepository;
	private final QaInputSnapshotRepository qaInputSnapshotRepository;
	private final AgentExecutionRepository agentExecutionRepository;

	QaSemanticReviewOrchestrator(
			BoundedRetryAgentRunner boundedRetryAgentRunner,
			AgentDefinitionLoader agentDefinitionLoader,
			OutputContractParser outputContractParser,
			ArtifactSchemaValidator artifactSchemaValidator,
			SemanticQaReviewOutputIdentityValidator identityValidator,
			CandidateOutputRepository candidateOutputRepository,
			QaExecutionRepository qaExecutionRepository,
			QaInputSnapshotRepository qaInputSnapshotRepository,
			AgentExecutionRepository agentExecutionRepository) {
		this.boundedRetryAgentRunner = boundedRetryAgentRunner;
		this.agentDefinitionLoader = agentDefinitionLoader;
		this.outputContractParser = outputContractParser;
		this.artifactSchemaValidator = artifactSchemaValidator;
		this.identityValidator = identityValidator;
		this.candidateOutputRepository = candidateOutputRepository;
		this.qaExecutionRepository = qaExecutionRepository;
		this.qaInputSnapshotRepository = qaInputSnapshotRepository;
		this.agentExecutionRepository = agentExecutionRepository;
	}

	/**
	 * {@code qaExecutionInputJson} must already be the validated, immutable {@code
	 * qa-execution-input} content ({@link ai.architech.backend.core.validation.QAExecutionPreflightValidator}'s
	 * own precondition, run by the caller before this is ever invoked - this method does not
	 * re-run preflight itself).
	 */
	public QaSemanticReviewResult run(
			UUID projectId,
			UUID testedCandidateId,
			String qaProfileRef,
			String executionSurfaceRef,
			String toolCapabilityProfileRef,
			String qaExecutionInputJson) {
		RunnerResult runnerResult = boundedRetryAgentRunner.runWithRetriesUsingInputArtifacts(
				projectId, AGENT_ID, AGENT_VERSION, Map.of(INPUT_TYPE, qaExecutionInputJson));

		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				runnerResult.execution().getId(), testedCandidateId, qaProfileRef, executionSurfaceRef, toolCapabilityProfileRef));
		qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), qaExecutionInputJson));

		return validateAndRecord(qaExecution, qaExecutionInputJson, runnerResult);
	}

	/**
	 * The validation half of the lifecycle, exposed separately so it is testable against a
	 * hand-built candidate without needing a real AI provider - same reasoning as {@code
	 * DesignerAgentRunner#validateAndPersist}.
	 */
	public QaSemanticReviewResult validateAndRecord(QaExecution qaExecution, String qaExecutionInputJson, RunnerResult runnerResult) {
		AgentExecution execution = runnerResult.execution();
		AgentDefinition agentDefinition = agentDefinitionLoader.resolve(AGENT_ID, AGENT_VERSION);
		AgentArtifactOutput outputContract = agentDefinition.outputs().artifacts().stream()
				.filter(output -> OUTPUT_TYPE.equals(output.type()))
				.findFirst()
				.orElseThrow();

		List<String> issues = new ArrayList<>();

		OutputContractResult contractResult = outputContractParser.parse(runnerResult.candidateOutput(), Set.of(OUTPUT_TYPE));
		contractResult.issues().forEach(issue -> issues.add("output-contract: " + issue));

		if (!contractResult.valid()) {
			log.warn(
					"Website QA Agent output failed output-contract validation for execution {}: {}",
					execution.getId(),
					runnerResult.candidateOutput());
			return failExecution(execution, qaExecution, null, issues);
		}

		String candidateJson = contractResult.artifactContentByType().get(OUTPUT_TYPE);
		CandidateOutput candidate = candidateOutputRepository.saveAndFlush(new CandidateOutput(execution.getId(), OUTPUT_TYPE, candidateJson));

		artifactSchemaValidator.validate(outputContract.schemaContent(), candidateJson).issues()
				.forEach(issue -> issues.add("schema: " + issue.path() + ": " + issue.message()));

		if (issues.isEmpty()) {
			for (SemanticQaReviewOutputIssue issue : identityValidator.validate(candidateJson, qaExecutionInputJson)) {
				issues.add("identity: " + issue.path() + ": " + issue.message());
			}
		}

		if (issues.isEmpty()) {
			execution.succeed();
		} else {
			execution.fail(String.join("; ", issues));
		}
		agentExecutionRepository.save(execution);

		return new QaSemanticReviewResult(execution, qaExecution, candidate, issues);
	}

	private QaSemanticReviewResult failExecution(
			AgentExecution execution, QaExecution qaExecution, CandidateOutput candidate, List<String> issues) {
		execution.fail(String.join("; ", issues));
		agentExecutionRepository.save(execution);
		return new QaSemanticReviewResult(execution, qaExecution, candidate, issues);
	}
}
