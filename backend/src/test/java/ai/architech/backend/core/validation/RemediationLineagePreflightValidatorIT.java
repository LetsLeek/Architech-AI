package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.CandidateFinding;
import ai.architech.backend.core.qa.CandidateFindingRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import ai.architech.backend.core.verification.RunnerVerificationRun;
import ai.architech.backend.core.verification.RunnerVerificationRunRepository;
import ai.architech.backend.core.verification.VerificationOutcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-179's own named lineage scenarios: valid remediation lineage,
 * cross-variant remediation rejection, and unresolvable previous Candidate/QA Result/Finding
 * references.
 */
@SpringBootTest
@Transactional
class RemediationLineagePreflightValidatorIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private RunnerVerificationRunRepository runnerVerificationRunRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private CandidateFindingRepository candidateFindingRepository;

	@Autowired
	private RemediationLineagePreflightValidator validator;

	@Test
	void validRemediationLineagePasses() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate previousCandidate = seedVerifiedCandidate(projectId, "prop-a");
		CandidateFinding finding = seedFinding(projectId, previousCandidate.getId());
		QaResult previousResult = seedQaResult(projectId, previousCandidate, finding);
		WebsiteImplementationCandidate newCandidate = seedVerifiedCandidateWithSameDesign(projectId, previousCandidate);

		String input = inputFor(newCandidate, previousCandidate, previousResult, finding);

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).as(result.issues().toString()).isTrue();
	}

	@Test
	void crossVariantRemediationIsRejected() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate previousCandidate = seedVerifiedCandidate(projectId, "prop-a");
		CandidateFinding finding = seedFinding(projectId, previousCandidate.getId());
		QaResult previousResult = seedQaResult(projectId, previousCandidate, finding);
		// The new target Candidate is a DIFFERENT Variant Lineage ("prop-b").
		WebsiteImplementationCandidate newCandidate = seedVerifiedCandidate(projectId, "prop-b");

		String input = inputFor(newCandidate, previousCandidate, previousResult, finding);

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("cross-variant remediation is not allowed"));
	}

	@Test
	void aNonExistentPreviousCandidateIsRejected() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate previousCandidate = seedVerifiedCandidate(projectId, "prop-a");
		CandidateFinding finding = seedFinding(projectId, previousCandidate.getId());
		QaResult previousResult = seedQaResult(projectId, previousCandidate, finding);
		WebsiteImplementationCandidate newCandidate = seedVerifiedCandidateWithSameDesign(projectId, previousCandidate);

		String input = inputFor(newCandidate, previousCandidate, previousResult, finding)
				.replace(previousCandidate.getId().toString(), UUID.randomUUID().toString());

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("no Candidate exists"));
	}

	@Test
	void aFindingBelongingToAnotherCandidateIsRejected() {
		UUID projectId = seedProject();
		WebsiteImplementationCandidate previousCandidate = seedVerifiedCandidate(projectId, "prop-a");
		WebsiteImplementationCandidate otherCandidate = seedVerifiedCandidate(projectId, "prop-a");
		CandidateFinding foreignFinding = seedFinding(projectId, otherCandidate.getId());
		QaResult previousResult = seedQaResult(projectId, previousCandidate, foreignFinding);
		WebsiteImplementationCandidate newCandidate = seedVerifiedCandidateWithSameDesign(projectId, previousCandidate);

		String input = inputFor(newCandidate, previousCandidate, previousResult, foreignFinding);

		PreExecutionValidationResult result = validator.validate(projectId, input);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("not the referenced previous Candidate"));
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

	private WebsiteImplementationCandidate seedVerifiedCandidate(UUID projectId, String proposalLocalRef) {
		AgentExecution developerExecution = seedSucceededDeveloperExecution(projectId);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), "design-v1", proposalLocalRef, "runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));
		runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(developerExecution.getId(), candidate.getRepositoryStateRef(), VerificationOutcome.PASS));
		return candidate;
	}

	private WebsiteImplementationCandidate seedVerifiedCandidateWithSameDesign(UUID projectId, WebsiteImplementationCandidate original) {
		AgentExecution developerExecution = seedSucceededDeveloperExecution(projectId);
		WebsiteImplementationCandidate candidate = candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), original.getSourceDesignArtifactVersionRef(),
				original.getSourceDesignProposalLocalRef(), original.getRuntimeProfileRef(),
				"snapshot-hash-" + UUID.randomUUID(), "remediated summary", "[]", "[]", "[]"));
		runnerVerificationRunRepository.saveAndFlush(
				new RunnerVerificationRun(developerExecution.getId(), candidate.getRepositoryStateRef(), VerificationOutcome.PASS));
		return candidate;
	}

	private CandidateFinding seedFinding(UUID projectId, UUID testedCandidateId) {
		AgentExecution qaAgentExecution = new AgentExecution(projectId, "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), testedCandidateId, "website-qa-full-release@1.0.0", null, "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));

		UUID qaResultId = UUID.randomUUID();
		CandidateFinding finding = new CandidateFinding(
				qaResultId, qaExecution.getId(), testedCandidateId, "NAV_TARGET_MISMATCH", "NAVIGATION", "MINOR",
				"[{\"type\":\"SOURCE_DESIGN\",\"ref\":\"design-b-3\"}]", "a summary", null, null, "[\"evidence-1\"]",
				"fingerprint-" + UUID.randomUUID(), "{\"detectionMethod\":\"SEMANTIC\"}");
		qaResultRepository.saveAndFlush(new QaResult(
				qaResultId, qaExecution.getId(), testedCandidateId, "website-qa-full-release@1.0.0", inputSnapshot.getId(),
				"COMPLETE", "[]", "[\"" + finding.getId() + "\"]", "[]", "[]", "[]", "[]", "HOLD",
				"[\"BLOCKING_CANDIDATE_FINDING\"]", evidenceManifest.getId(), "{\"qaSystemVersion\":\"1.0.0\"}"));

		return candidateFindingRepository.saveAndFlush(finding);
	}

	/** A second, distinct QaResult representing the "previous QA Result" the remediation cycle originated from - deliberately not the finding's own qaResultId, to prove independent ref resolution. */
	private QaResult seedQaResult(UUID projectId, WebsiteImplementationCandidate candidate, CandidateFinding finding) {
		AgentExecution qaAgentExecution = new AgentExecution(projectId, "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution qaExecution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", null, "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(qaExecution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(qaExecution.getId()));
		return qaResultRepository.saveAndFlush(new QaResult(
				UUID.randomUUID(), qaExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", inputSnapshot.getId(),
				"COMPLETE", "[]", "[\"" + finding.getId() + "\"]", "[]", "[]", "[]", "[]", "HOLD",
				"[\"BLOCKING_CANDIDATE_FINDING\"]", evidenceManifest.getId(), "{\"qaSystemVersion\":\"1.0.0\"}"));
	}

	private String inputFor(
			WebsiteImplementationCandidate targetCandidate,
			WebsiteImplementationCandidate previousCandidate,
			QaResult previousResult,
			CandidateFinding finding) {
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
				  "remediationContext": {
				    "previousCandidateRef": "%s",
				    "previousQAResultRef": "%s",
				    "previousFindingRefs": ["%s"]
				  },
				  "provenance": {
				    "inputSnapshotRef": "input-snapshot-fixture",
				    "qaAgentVersion": "1.0.0"
				  }
				}
				"""
				.formatted(
						targetCandidate.getId(), targetCandidate.getSourceDesignRef(), targetCandidate.getRuntimeProfileRef(),
						previousCandidate.getId(), previousResult.getId(), finding.getId());
	}
}
