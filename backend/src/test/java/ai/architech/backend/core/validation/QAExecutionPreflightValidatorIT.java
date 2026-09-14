package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import ai.architech.backend.core.verification.VerificationOutcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-169's own acceptance criteria against a realistic Candidate/Runner-
 * Verification graph: valid input, wrong Source Design, a stale/wrong Candidate, and the QA
 * authority ref checks.
 */
@SpringBootTest
@Transactional
class QAExecutionPreflightValidatorIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private RunnerVerificationRunRepository runnerVerificationRunRepository;

	@Autowired
	private QAExecutionPreflightValidator validator;

	@Test
	void acceptsAWellFormedInputAgainstAVerifiedCandidate() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate(projectId);

		PreExecutionValidationResult result = validator.validate(projectId, inputFor(candidate));

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void rejectsAWrongSourceDesignRef() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate(projectId);
		String input = inputFor(candidate).replace(candidate.getSourceDesignRef(), "design-x:prop-z");

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("productAuthority/sourceDesignRef"));
	}

	@Test
	void rejectsAStaleCandidateThatFailedItsMostRecentVerification() {
		UUID projectId = seedProject();
		AgentExecution developerExecution = seedSucceededDeveloperExecution(projectId);
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, developerExecution, "snapshot-stale");
		// A later source mutation invalidated the previously-PASSed state - the Candidate itself
		// still claims "snapshot-stale", but Runner Verification most recently ran (and FAILed)
		// against a *different* repository state, so there is no PASS matching the Candidate's own.
		runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(developerExecution.getId(), "snapshot-mutated", VerificationOutcome.FAIL));

		PreExecutionValidationResult result = validator.validate(projectId, inputFor(candidate));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("target/candidateRef"));
	}

	@Test
	void rejectsAReferenceToACandidateThatDoesNotExist() {
		UUID projectId = seedProject();
		String input = inputFor(UUID.randomUUID(), "design-v1:prop-a", "runtime-v1");

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("target/candidateRef"));
	}

	@Test
	void rejectsACandidateBelongingToAnotherProject() {
		UUID projectId = seedProject();
		UUID otherProjectId = seedProject();
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate(otherProjectId);

		PreExecutionValidationResult result = validator.validate(projectId, inputFor(candidate));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("target/candidateRef"));
	}

	@Test
	void rejectsAnUnknownQaProfileRef() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate candidate = seedVerifiedCandidate(projectId);
		String input = inputFor(candidate).replace("website-qa-full-release@1.0.0", "website-qa-made-up@1.0.0");

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("qaAuthority/qaProfileRef"));
	}

	private UUID seedProject() {
		return projectRepository.saveAndFlush(new Project("website")).getId();
	}

	private AgentExecution seedSucceededDeveloperExecution(UUID projectId) {
		AgentExecution execution = new AgentExecution(projectId, "developer-agent", 1);
		execution.start();
		execution.succeed();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private WebsiteImplementationCandidate seedCandidate(UUID projectId, AgentExecution developerExecution, String repositoryStateRef) {
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId,
				developerExecution.getId(),
				"design-v1",
				"prop-a",
				"runtime-v1",
				repositoryStateRef,
				"summary",
				"[]",
				"[]",
				"[]"));
	}

	private WebsiteImplementationCandidate seedVerifiedCandidate(UUID projectId) {
		AgentExecution developerExecution = seedSucceededDeveloperExecution(projectId);
		WebsiteImplementationCandidate candidate = seedCandidate(projectId, developerExecution, "snapshot-hash-" + UUID.randomUUID());
		runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(developerExecution.getId(), candidate.getRepositoryStateRef(), VerificationOutcome.PASS));
		return candidate;
	}

	private String inputFor(WebsiteImplementationCandidate candidate) {
		return inputFor(candidate.getId(), candidate.getSourceDesignRef(), candidate.getRuntimeProfileRef());
	}

	private String inputFor(UUID candidateId, String sourceDesignRef, String runtimeProfileRef) {
		return """
				{
				  "schemaVersion": "1.0.0",
				  "qaExecutionRef": "qa-exec-fixture",
				  "target": {"candidateRef": "%s"},
				  "productAuthority": {
				    "customerProfileRef": "customer-profile-fixture",
				    "websiteRequirementsRef": "requirements-fixture",
				    "sourceDesignRef": "%s",
				    "runtimeProfileRef": "%s",
				    "integrationContractRefs": []
				  },
				  "qaAuthority": {
				    "qaProfileRef": "website-qa-full-release@1.0.0",
				    "ruleSetRef": "website-qa-rules@1.0.0",
				    "skillSetRef": "website-qa-skills@1.0.0",
				    "validatorSetRef": "website-qa-validator-set@1.0.0",
				    "findingTaxonomyRef": "website-qa-finding-taxonomy@1.0.0",
				    "checkRegistryRef": "website-qa-check-registry@1.0.0"
				  },
				  "executionContext": {
				    "toolCapabilityProfileRef": "website-qa-tools@1.0.0"
				  },
				  "provenance": {
				    "inputSnapshotRef": "input-snapshot-fixture",
				    "qaAgentVersion": "1.0.0"
				  }
				}
				"""
				.formatted(candidateId, sourceDesignRef, runtimeProfileRef);
	}
}
