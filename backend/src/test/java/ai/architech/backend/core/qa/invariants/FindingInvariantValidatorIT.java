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

/** Real-Postgres proof of AIW-174's own acceptance criteria: invented code, invalid severity, missing evidence. */
@SpringBootTest
@Transactional
class FindingInvariantValidatorIT {

	private static final String QA_INPUT_SNAPSHOT = """
			{"productAuthority": {"websiteRequirementsRef": "requirements-ref-7"}}
			""";

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
	private FindingTaxonomyLoader taxonomyLoader;

	@Autowired
	private FindingInvariantValidator validator;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void aWellFormedCandidateBackedByRealEvidenceHasNoIssues() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(candidate);

		JsonNode findingCandidate = candidateNode("REQ_MUST_UNFULFILLED", "REQUIREMENT_FULFILLMENT", "MAJOR",
				"WEBSITE_REQUIREMENT", "requirements-ref-7", evidence.getId().toString());

		List<FindingInvariantIssue> issues =
				validator.validate(findingCandidate, taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).isEmpty();
	}

	@Test
	void anInventedFindingCodeIsRejected() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(candidate);

		JsonNode findingCandidate = candidateNode("MADE_UP_CODE", "REQUIREMENT_FULFILLMENT", "MAJOR",
				"WEBSITE_REQUIREMENT", "requirements-ref-7", evidence.getId().toString());

		List<FindingInvariantIssue> issues =
				validator.validate(findingCandidate, taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("findingCode"));
	}

	@Test
	void aSeverityOutsideTheTaxonomyBoundsIsRejected() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(candidate);

		// REQ_MUST_UNFULFILLED bounds are [MAJOR, CRITICAL] - MINOR is out of bounds.
		JsonNode findingCandidate = candidateNode("REQ_MUST_UNFULFILLED", "REQUIREMENT_FULFILLMENT", "MINOR",
				"WEBSITE_REQUIREMENT", "requirements-ref-7", evidence.getId().toString());

		List<FindingInvariantIssue> issues =
				validator.validate(findingCandidate, taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("proposedSeverity"));
	}

	@Test
	void aDisallowedNormativeBasisTypeIsRejected() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(candidate);

		// REQ_MUST_UNFULFILLED only allows WEBSITE_REQUIREMENT, not CUSTOMER_PROFILE.
		JsonNode findingCandidate = candidateNode("REQ_MUST_UNFULFILLED", "REQUIREMENT_FULFILLMENT", "MAJOR",
				"CUSTOMER_PROFILE", "requirements-ref-7", evidence.getId().toString());

		List<FindingInvariantIssue> issues =
				validator.validate(findingCandidate, taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).contains("normativeBasis[0].type"));
	}

	@Test
	void aNormativeBasisRefNotInTheSnapshotIsRejected() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(candidate);

		JsonNode findingCandidate = candidateNode("REQ_MUST_UNFULFILLED", "REQUIREMENT_FULFILLMENT", "MAJOR",
				"WEBSITE_REQUIREMENT", "some-ref-not-in-the-snapshot", evidence.getId().toString());

		List<FindingInvariantIssue> issues =
				validator.validate(findingCandidate, taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).contains("normativeBasis[0].ref"));
	}

	@Test
	void anUnknownSeverityValueIsRejected() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(candidate);

		JsonNode findingCandidate = candidateNode("REQ_MUST_UNFULFILLED", "REQUIREMENT_FULFILLMENT", "SUPER_CRITICAL",
				"WEBSITE_REQUIREMENT", "requirements-ref-7", evidence.getId().toString());

		List<FindingInvariantIssue> issues =
				validator.validate(findingCandidate, taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("proposedSeverity"));
	}

	@Test
	void aCandidateWithNoNormativeBasisArrayStillValidatesEverythingElse() {
		WebsiteImplementationCandidate candidate = seedCandidate();
		EvidenceRecord evidence = seedEvidence(candidate);
		String json = """
				{
				  "localRef": "finding-1",
				  "findingCode": "REQ_MUST_UNFULFILLED",
				  "primaryDomain": "REQUIREMENT_FULFILLMENT",
				  "proposedSeverity": "MAJOR",
				  "summary": "a summary",
				  "evidenceRefs": ["%s"]
				}
				"""
				.formatted(evidence.getId());

		List<FindingInvariantIssue> issues =
				validator.validate(objectMapper.readTree(json), taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).isEmpty();
	}

	@Test
	void missingEvidenceIsRejectedViaEvidenceBindingValidator() {
		WebsiteImplementationCandidate candidate = seedCandidate();

		JsonNode findingCandidate = candidateNode("REQ_MUST_UNFULFILLED", "REQUIREMENT_FULFILLMENT", "MAJOR",
				"WEBSITE_REQUIREMENT", "requirements-ref-7", UUID.randomUUID().toString());

		List<FindingInvariantIssue> issues =
				validator.validate(findingCandidate, taxonomyLoader.load(), candidate.getId(), QA_INPUT_SNAPSHOT);

		assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("evidenceRefs"));
	}

	private JsonNode candidateNode(String findingCode, String domain, String severity, String basisType, String basisRef, String evidenceRef) {
		String json = """
				{
				  "localRef": "finding-1",
				  "findingCode": "%s",
				  "primaryDomain": "%s",
				  "proposedSeverity": "%s",
				  "normativeBasis": [{"type": "%s", "ref": "%s"}],
				  "summary": "a summary",
				  "evidenceRefs": ["%s"]
				}
				"""
				.formatted(findingCode, domain, severity, basisType, basisRef, evidenceRef);
		return objectMapper.readTree(json);
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
