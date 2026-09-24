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

/**
 * Integration coverage for the frozen Requirements V1 pipeline (AIW-52). Every scenario here
 * runs the real validators/persistence against a real Postgres - only the AI Gateway call
 * itself is a hand-built {@link RunnerResult} rather than a live model, since the platform's
 * only registered provider is the deterministic mock (it always returns empty content and so
 * can never itself produce a validation-passing candidate); this keeps every test
 * deterministic without depending on an unbounded live AI loop, per the ticket's own
 * acceptance criterion.
 *
 * <p>Not duplicated here: "retry creates a distinct AgentExecution" is already proven by
 * {@code BoundedRetryAgentRunnerTests#createsOneNewAgentExecutionPerAttemptAndThrowsAfterExhaustingTheBudget}
 * (AIW-38) - {@link ai.architech.backend.core.runner.BoundedRetryAgentRunner}, which
 * {@link RequirementsAnalysisRunner#run} delegates retries to unmodified, has no
 * Requirements-Agent-specific behavior to re-prove here.
 */
@SpringBootTest
@Transactional
class RequirementsAnalysisRunnerIT {

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
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).hasSize(2);
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
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
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
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsButStillRecordsCandidateOutputsAsAuditWhenTheWebsiteRequirementsFailSchemaValidation() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID snapshotId = seedTwoSourceRefs(project);
		AgentExecution execution = startedExecution(project);

		// mirror of the customer-profile-invalid case above, other direction: valid Customer
		// Profile, Website Requirements missing most of its required top-level keys.
		String candidateOutput = combine(VALID_CUSTOMER_PROFILE, "{\"goals\": []}");

		RequirementsAnalysisResult result =
				requirementsAnalysisRunner.validateAndPersist(snapshotId, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.validationIssues()).anyMatch(issue -> issue.startsWith("schema[website-requirements]"));
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(2);
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsWhenALocalRefIsDuplicatedWithinAnArtifact() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID snapshotId = seedTwoSourceRefs(project);
		AgentExecution execution = startedExecution(project);

		// schema-valid (both locations satisfy the schema's anyOf via "name") and cites no
		// sourceRefs at all, so schema/cross-artifact stay clean - only the local-ref layer
		// should flag anything here.
		String duplicateLocalRefProfile =
				"""
				{
				  "business": {}, "contact": {},
				  "locations": [{"localRef": "loc-1", "name": "HQ"}, {"localRef": "loc-1", "name": "Branch"}],
				  "offerings": [], "openingHours": [], "socialLinks": [], "providedClaims": [],
				  "unknowns": [], "conflicts": [], "provenance": []
				}""";
		String candidateOutput = combine(duplicateLocalRefProfile, VALID_WEBSITE_REQUIREMENTS);

		RequirementsAnalysisResult result =
				requirementsAnalysisRunner.validateAndPersist(snapshotId, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues())
				.anyMatch(issue -> issue.startsWith("local-ref[customer-profile]") && issue.contains("loc-1"));
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsWhenAnArtifactFailsOnlySemanticValidation() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		UUID snapshotId = seedTwoSourceRefs(project);
		AgentExecution execution = startedExecution(project);

		// schema-valid (customType is optional in the schema itself) and cites only SRC-1, so
		// schema/local-ref/cross-artifact all stay clean - only the semantic
		// type=custom<=>customType consistency rule should flag this.
		String customTypeMissingRequirements =
				"""
				{
				  "goals": [], "targetAudiences": [], "functionalRequirements": [],
				  "languages": [], "constraints": [], "unknowns": [], "conflicts": [],
				  "contentRequirements": [{"localRef": "cr-1", "type": "custom", "description": "x", "strength": "must", "sourceRefs": ["SRC-1"]}]
				}""";
		String candidateOutput = combine(VALID_CUSTOMER_PROFILE, customTypeMissingRequirements);

		RequirementsAnalysisResult result =
				requirementsAnalysisRunner.validateAndPersist(snapshotId, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues())
				.anyMatch(issue -> issue.startsWith("semantic[website-requirements]") && issue.contains("customType"));
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
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
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void endToEndRunFailsWithTheMockProviderSinceItNeverProducesValidJson() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		projectInputRepository.saveAndFlush(new ProjectInput(project.getId(), "We are a bakery in Vienna."));

		RequirementsAnalysisResult result = requirementsAnalysisRunner.run(project.getId());

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.execution().getAgentId()).isEqualTo("requirements-agent");
		assertThat(artifactVersionRepository.findByAgentExecutionId(result.execution().getId())).isEmpty();
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
