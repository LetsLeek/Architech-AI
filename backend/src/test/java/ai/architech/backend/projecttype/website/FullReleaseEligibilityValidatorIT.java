package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import ai.architech.backend.core.qa.QaInputSnapshot;
import ai.architech.backend.core.qa.QaInputSnapshotRepository;
import ai.architech.backend.core.qa.QaResult;
import ai.architech.backend.core.qa.QaResultRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-Postgres proof of AIW-178's own named scenarios: clean PASS, blocking Finding, authority
 * escalation, evaluation incompleteness, stale result.
 */
@SpringBootTest
@Transactional
class FullReleaseEligibilityValidatorIT {

	private static final String PROFILE_REF = "website-qa-full-release@1.0.0";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private QaInputSnapshotRepository qaInputSnapshotRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private QaResultRepository qaResultRepository;

	@Autowired
	private FullReleaseEligibilityValidator validator;

	@Test
	void aCleanPassMakesTheReleasePathCandidateEligible() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		seedQaResult(candidate, "PASS", "[]");

		FullReleaseEligibility eligibility = validator.validate(candidate.getId(), PROFILE_REF);

		assertThat(eligibility.eligible()).isTrue();
		assertThat(eligibility.releasePathCandidateId()).isEqualTo(candidate.getId());
	}

	@Test
	void aBlockingFindingHoldMakesTheCandidateIneligible() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		seedQaResult(candidate, "HOLD", "[\"BLOCKING_CANDIDATE_FINDING\"]");

		FullReleaseEligibility eligibility = validator.validate(candidate.getId(), PROFILE_REF);

		assertThat(eligibility.eligible()).isFalse();
	}

	@Test
	void anAuthorityEscalationHoldMakesTheCandidateIneligible() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		seedQaResult(candidate, "HOLD", "[\"AUTHORITY_RESOLUTION_REQUIRED\"]");

		FullReleaseEligibility eligibility = validator.validate(candidate.getId(), PROFILE_REF);

		assertThat(eligibility.eligible()).isFalse();
	}

	@Test
	void anEvaluationIncompleteHoldMakesTheCandidateIneligible() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		seedQaResult(candidate, "HOLD", "[\"EVALUATION_INCOMPLETE\"]");

		FullReleaseEligibility eligibility = validator.validate(candidate.getId(), PROFILE_REF);

		assertThat(eligibility.eligible()).isFalse();
	}

	@Test
	void aPassAgainstAReplacedCandidateNeverQualifiesTheNewReleasePathCandidate() {
		WebsiteImplementationCandidate originalCandidate = seedCandidate();
		seedQaResult(originalCandidate, "PASS", "[]");

		// The release path pointer moved to a remediated, distinct Candidate - the old PASS
		// belongs to the old id and can never satisfy the new one.
		WebsiteImplementationCandidate remediatedCandidate = seedCandidate();

		FullReleaseEligibility eligibility = validator.validate(remediatedCandidate.getId(), PROFILE_REF);

		assertThat(eligibility.eligible()).isFalse();
	}

	@Test
	void aPassUnderTheWrongProfileRefDoesNotQualify() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		seedQaResult(candidate, "PASS", "[]");

		FullReleaseEligibility eligibility = validator.validate(candidate.getId(), "website-qa-comparison-readiness@1.0.0");

		assertThat(eligibility.eligible()).isFalse();
	}

	private WebsiteImplementationCandidate seedCandidate() {
		UUID projectId = projectRepository.saveAndFlush(new Project("website")).getId();
		AgentExecution developerExecution = new AgentExecution(projectId, "developer-agent", 1);
		developerExecution.start();
		developerExecution.succeed();
		agentExecutionRepository.saveAndFlush(developerExecution);
		return candidateRepository.saveAndFlush(new WebsiteImplementationCandidate(
				projectId, developerExecution.getId(), "design-v1", "prop-a", "runtime-v1",
				"snapshot-hash-" + UUID.randomUUID(), "summary", "[]", "[]", "[]"));
	}

	private QaResult seedQaResult(WebsiteImplementationCandidate candidate, String gateOutcome, String holdReasonsJson) {
		AgentExecution qaAgentExecution = new AgentExecution(candidate.getProjectId(), "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution execution = qaExecutionRepository.saveAndFlush(
				new QaExecution(qaAgentExecution.getId(), candidate.getId(), PROFILE_REF, null, "website-qa-tools@1.0.0"));
		QaInputSnapshot inputSnapshot = qaInputSnapshotRepository.saveAndFlush(new QaInputSnapshot(execution.getId(), "{}"));
		EvidenceManifest evidenceManifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(execution.getId()));
		return qaResultRepository.saveAndFlush(new QaResult(
				UUID.randomUUID(), execution.getId(), candidate.getId(), PROFILE_REF, inputSnapshot.getId(),
				"COMPLETE", "[]", "[]", "[]", "[]", "[]", "[]", gateOutcome, holdReasonsJson,
				evidenceManifest.getId(), "{\"qaSystemVersion\":\"1.0.0\"}"));
	}
}
