package ai.architech.backend.core.qa.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidateRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.qa.EvidenceManifest;
import ai.architech.backend.core.qa.EvidenceManifestRepository;
import ai.architech.backend.core.qa.EvidenceRecord;
import ai.architech.backend.core.qa.EvidenceRecordRepository;
import ai.architech.backend.core.qa.QaExecution;
import ai.architech.backend.core.qa.QaExecutionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Postgres proof of AIW-179's own named scenarios: resolved, persists, changed,
 * not-evaluable, and their corresponding rejection cases.
 */
@SpringBootTest
@Transactional
class RemediationAssessmentInvariantValidatorIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private WebsiteImplementationCandidateRepository candidateRepository;

	@Autowired
	private QaExecutionRepository qaExecutionRepository;

	@Autowired
	private EvidenceManifestRepository evidenceManifestRepository;

	@Autowired
	private EvidenceRecordRepository evidenceRecordRepository;

	@Autowired
	private RemediationAssessmentInvariantValidator validator;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void resolvedWithRealNewCandidateEvidencePasses() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(newCandidate);

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "RESOLVED",
				 "evidenceRefs": ["%s"], "relatedFindingCandidateRefs": []}
				""".formatted(evidence.getId()));
		JsonNode output = emptyOutput();

		assertThat(validator.validate(candidate, output, newCandidate.getId())).isEmpty();
	}

	@Test
	void resolvedWithNoEvidenceIsRejected() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "RESOLVED",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": []}
				""");

		List<FindingInvariantIssue> issues = validator.validate(candidate, emptyOutput(), newCandidate.getId());

		assertThat(issues).anySatisfy(i -> assertThat(i.message()).contains("RESOLVED requires"));
	}

	@Test
	void resolvedWithEvidenceFromAForeignCandidateIsRejected() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();
		WebsiteImplementationCandidate foreignCandidate = seedCandidate();
		EvidenceRecord foreignEvidence = seedEvidence(foreignCandidate);

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "RESOLVED",
				 "evidenceRefs": ["%s"], "relatedFindingCandidateRefs": []}
				""".formatted(foreignEvidence.getId()));

		List<FindingInvariantIssue> issues = validator.validate(candidate, emptyOutput(), newCandidate.getId());

		assertThat(issues).isNotEmpty();
	}

	@Test
	void persistsReferencingARealFindingCandidatePasses() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "PERSISTS",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": ["finding-local-1"]}
				""");
		JsonNode output = objectMapper.readTree("""
				{"findingCandidates": [{"localRef": "finding-local-1"}], "evaluationIssueCandidates": []}
				""");

		assertThat(validator.validate(candidate, output, newCandidate.getId())).isEmpty();
	}

	@Test
	void changedReferencingARealFindingCandidatePasses() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "CHANGED",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": ["finding-local-1"]}
				""");
		JsonNode output = objectMapper.readTree("""
				{"findingCandidates": [{"localRef": "finding-local-1"}], "evaluationIssueCandidates": []}
				""");

		assertThat(validator.validate(candidate, output, newCandidate.getId())).isEmpty();
	}

	@Test
	void persistsWithNoRelatedFindingsIsRejected() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "PERSISTS",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": []}
				""");

		List<FindingInvariantIssue> issues = validator.validate(candidate, emptyOutput(), newCandidate.getId());

		assertThat(issues).anySatisfy(i -> assertThat(i.message()).contains("PERSISTS/CHANGED requires"));
	}

	@Test
	void persistsReferencingAnUnknownFindingCandidateIsRejected() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "PERSISTS",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": ["does-not-exist"]}
				""");

		List<FindingInvariantIssue> issues = validator.validate(candidate, emptyOutput(), newCandidate.getId());

		assertThat(issues).anySatisfy(i -> assertThat(i.message()).contains("does not resolve"));
	}

	@Test
	void notEvaluableBackedByARealEvaluationIssuePasses() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "NOT_EVALUABLE",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": []}
				""");
		JsonNode output = objectMapper.readTree("""
				{"findingCandidates": [], "evaluationIssueCandidates": [{"code": "TOOL_FAILURE"}]}
				""");

		assertThat(validator.validate(candidate, output, newCandidate.getId())).isEmpty();
	}

	@Test
	void notEvaluableWithNoBackingEvaluationIssueIsRejected() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "NOT_EVALUABLE",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": []}
				""");

		List<FindingInvariantIssue> issues = validator.validate(candidate, emptyOutput(), newCandidate.getId());

		assertThat(issues).anySatisfy(i -> assertThat(i.message()).contains("never a guess"));
	}

	@Test
	void anUnknownProposedStatusIsRejected() {
		WebsiteImplementationCandidate newCandidate = seedCandidate();

		JsonNode candidate = node("""
				{"previousFindingRef": "finding-1", "proposedStatus": "MADE_UP",
				 "evidenceRefs": [], "relatedFindingCandidateRefs": []}
				""");

		List<FindingInvariantIssue> issues = validator.validate(candidate, emptyOutput(), newCandidate.getId());

		assertThat(issues).anySatisfy(i -> assertThat(i.message()).contains("not a known remediation status"));
	}

	private JsonNode node(String json) {
		return objectMapper.readTree(json);
	}

	private JsonNode emptyOutput() {
		return objectMapper.readTree("""
				{"findingCandidates": [], "evaluationIssueCandidates": []}
				""");
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

	private EvidenceRecord seedEvidence(WebsiteImplementationCandidate candidate) {
		AgentExecution qaAgentExecution = new AgentExecution(candidate.getProjectId(), "website-qa-agent", 1);
		qaAgentExecution.start();
		agentExecutionRepository.saveAndFlush(qaAgentExecution);
		QaExecution execution = qaExecutionRepository.saveAndFlush(new QaExecution(
				qaAgentExecution.getId(), candidate.getId(), "website-qa-full-release@1.0.0", null, "website-qa-tools@1.0.0"));
		EvidenceManifest manifest = evidenceManifestRepository.saveAndFlush(new EvidenceManifest(execution.getId()));
		return evidenceRecordRepository.saveAndFlush(new EvidenceRecord(
				manifest.getId(), execution.getId(), candidate.getId(), EvidenceRecord.Kind.SOURCE_REFERENCE,
				"check:AUTHORITY_REFERENCE_INTEGRITY", null, null, null, null, "observed content", null, null));
	}
}
