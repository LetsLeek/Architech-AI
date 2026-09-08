package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.evidence.EvidenceSnapshot;
import ai.architech.backend.core.evidence.EvidenceSnapshotFactory;
import ai.architech.backend.core.evidence.SourceContext;
import ai.architech.backend.core.evidence.SourceContextFactory;
import ai.architech.backend.core.evidence.SourceRefAssigner;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.projectinput.ProjectInput;
import ai.architech.backend.core.projectinput.ProjectInputRepository;
import ai.architech.backend.core.runner.RunnerResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RequirementsAnalysisRunnerTests {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectInputRepository projectInputRepository;

	@Autowired
	private EvidenceSnapshotFactory evidenceSnapshotFactory;

	@Autowired
	private SourceContextFactory sourceContextFactory;

	@Autowired
	private SourceRefAssigner sourceRefAssigner;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private CandidateOutputRepository candidateOutputRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private RequirementsAnalysisRunner requirementsAnalysisRunner;

	// Schema-valid and semantic-valid on their own (mirrors the fixtures proven by
	// CustomerProfileSemanticValidatorTests/WebsiteRequirementsSemanticValidatorTests) - only
	// cites SRC-1/SRC-2, which seedTwoSourceRefs() below guarantees exist.
	private static final String VALID_CUSTOMER_PROFILE =
			"""
			{
			  "business": {"name": "Acme"},
			  "contact": {"phone": "+43 660 1234567"},
			  "locations": [{"localRef": "loc-1", "name": "HQ"}],
			  "offerings": [{"localRef": "off-1", "name": "Haircut", "price": {"type": "range", "amount": 10, "maxAmount": 20, "currency": "EUR", "raw": "10-20 EUR"}}],
			  "openingHours": [{"localRef": "oh-1", "raw": "Mon 9-12", "locationRefs": ["loc-1"], "schedule": [{"days": ["MO"], "intervals": [{"from": "09:00", "to": "12:00"}]}], "closedDays": ["SU"]}],
			  "socialLinks": [],
			  "providedClaims": [],
			  "unknowns": [{"kind": "ambiguous", "field": "business.name", "description": "unclear", "sourceRefs": ["SRC-1"]}],
			  "conflicts": [{"field": "contact.phone", "description": "conflict", "statements": [{"value": "A", "sourceRefs": ["SRC-1"]}, {"value": "B", "sourceRefs": ["SRC-2"]}]}],
			  "provenance": [{"targetRef": "loc-1", "field": "name", "sourceRefs": ["SRC-1"]}]
			}""";

	private static final String VALID_WEBSITE_REQUIREMENTS =
			"""
			{
			  "goals": [{"localRef": "goal-1", "description": "Grow leads", "strength": "must", "sourceRefs": ["SRC-1"]}],
			  "targetAudiences": [],
			  "contentRequirements": [{"localRef": "cr-1", "type": "custom", "customType": "recipe-index", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}],
			  "functionalRequirements": [{"localRef": "fr-1", "type": "contact-form", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}],
			  "languages": [{"code": "en", "strength": "must", "sourceRefs": ["SRC-1"]}, {"code": "de-AT", "strength": "should", "sourceRefs": ["SRC-1"]}],
			  "constraints": [],
			  "unknowns": [{"kind": "ambiguous", "field": "x", "description": "unclear", "affects": ["goal-1"], "sourceRefs": ["SRC-1"]}],
			  "conflicts": [{"description": "x", "affects": ["goal-1"], "statements": [{"description": "A", "sourceRefs": ["SRC-1"]}, {"description": "B", "sourceRefs": ["SRC-2"]}]}]
			}""";

	@Test
	void persistsBothCandidatesAsCanonicalAndSucceedsWhenEverythingValidates() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID snapshotId = seedTwoSourceRefs(project);
		AgentExecution execution = startedExecution(project);

		String candidateOutput = combine(VALID_CUSTOMER_PROFILE, VALID_WEBSITE_REQUIREMENTS);

		RequirementsAnalysisResult result =
				requirementsAnalysisRunner.validateAndPersist(snapshotId, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isTrue();
		assertThat(result.validationIssues()).isEmpty();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(artifactVersionRepository.findAll()).hasSize(2);
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(2);
	}

	@Test
	void failsAndPersistsNothingWhenCandidateIsNotValidJson() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID snapshotId = seedTwoSourceRefs(project);
		AgentExecution execution = startedExecution(project);

		RequirementsAnalysisResult result =
				requirementsAnalysisRunner.validateAndPersist(snapshotId, new RunnerResult(execution, "not json {{{"));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).isEmpty();
		assertThat(artifactVersionRepository.findAll()).isEmpty();
	}

	@Test
	void failsButStillRecordsCandidateOutputsAsAuditWhenSchemaValidationFails() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID snapshotId = seedTwoSourceRefs(project);
		AgentExecution execution = startedExecution(project);

		// output-contract only checks the two required top-level keys exist - it doesn't look
		// inside them, so this reaches schema validation and fails there instead.
		String candidateOutput = combine("{\"business\": {}}", VALID_WEBSITE_REQUIREMENTS);

		RequirementsAnalysisResult result =
				requirementsAnalysisRunner.validateAndPersist(snapshotId, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.validationIssues()).anyMatch(issue -> issue.startsWith("schema[customer-profile]"));
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(2);
		assertThat(artifactVersionRepository.findAll()).isEmpty();
	}

	@Test
	void failsWhenACitedSourceRefDoesNotBelongToTheActiveSnapshot() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID snapshotId = seedTwoSourceRefs(project);
		AgentExecution execution = startedExecution(project);

		String profileWithForeignRef = VALID_CUSTOMER_PROFILE.replace("SRC-1", "SRC-999");
		String candidateOutput = combine(profileWithForeignRef, VALID_WEBSITE_REQUIREMENTS);

		RequirementsAnalysisResult result =
				requirementsAnalysisRunner.validateAndPersist(snapshotId, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.startsWith("cross-artifact[customer-profile]"));
		assertThat(artifactVersionRepository.findAll()).isEmpty();
	}

	@Test
	void endToEndRunFailsWithTheMockProviderSinceItNeverProducesValidJson() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));

		RequirementsAnalysisResult result = requirementsAnalysisRunner.run(project.getId());

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.execution().getAgentId()).isEqualTo("requirements-agent");
		assertThat(artifactVersionRepository.findAll()).isEmpty();
	}

	private String combine(String customerProfileJson, String websiteRequirementsJson) {
		return "{\"customer-profile\": " + customerProfileJson + ", \"website-requirements\": " + websiteRequirementsJson + "}";
	}

	private AgentExecution startedExecution(Project project) {
		AgentExecution execution =
				agentExecutionRepository.saveAndFlush(new AgentExecution(project.getId(), "requirements-agent", 1));
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}

	private UUID seedTwoSourceRefs(Project project) {
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "First evidence."));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "Second evidence."));
		EvidenceSnapshot snapshot = evidenceSnapshotFactory.takeSnapshot(project.getId());
		SourceContext context = sourceContextFactory.build(snapshot.getId());
		sourceRefAssigner.assignRefs(context);
		return snapshot.getId();
	}
}
