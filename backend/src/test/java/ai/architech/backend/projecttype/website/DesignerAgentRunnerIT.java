package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.DesignProposalSetSemanticReviewer;
import ai.architech.backend.core.validation.SemanticReviewFinding;
import ai.architech.backend.core.validation.SemanticReviewResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration coverage for the Designer Agent V1 pipeline (AIW-124). Every scenario runs the
 * real validators/persistence against a real Postgres - only the AI Gateway call itself is a
 * hand-built {@link RunnerResult} rather than a live model, same reasoning as {@code
 * RequirementsAnalysisRunnerIT}. {@link DesignProposalSetSemanticReviewer} is mocked (not the
 * AI Gateway itself) so the one test that needs the pipeline to actually succeed isn't blocked
 * by the platform's real mock provider always returning empty content.
 */
@SpringBootTest
@Transactional
class DesignerAgentRunnerIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private CandidateOutputRepository candidateOutputRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private DesignerAgentRunner designerAgentRunner;

	@MockitoBean
	private DesignProposalSetSemanticReviewer designProposalSetSemanticReviewer;

	private static final String CUSTOMER_PROFILE = """
			{"locations": [{"localRef": "cust-1"}]}
			""";
	private static final String WEBSITE_REQUIREMENTS = """
			{"goals": [{"localRef": "req-1"}]}
			""";

	@Test
	void persistsTheCandidateAsCanonicalAndSucceedsWhenEveryStagePasses() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any())).thenReturn(SemanticReviewResult.passed());

		String candidateOutput = envelope(threeValidProposals());

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isTrue();
		assertThat(result.validationIssues()).isEmpty();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).hasSize(1);
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(1);
	}

	@Test
	void failsAndPersistsNothingWhenCandidateIsNotValidJson() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, "not json {{{"));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).isEmpty();
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsButStillRecordsTheCandidateAsAuditWhenSchemaValidationFails() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);

		// Only 2 proposals instead of the schema-required 3 - output-contract passes (the
		// top-level envelope is fine), JSON Schema validation fails.
		String candidateOutput = envelope("[" + proposal("a") + "," + proposal("b") + "]");

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.validationIssues()).anyMatch(issue -> issue.startsWith("schema:"));
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(1);
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsWhenAPageRefDoesNotResolveWithinItsOwnProposal() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);

		String brokenProposal = proposal("a")
				.replace(
						"\"elements\": [{\"localRef\": \"el-a\", \"kind\": \"heading\", \"role\": \"title\", \"contentIntent\": \"welcome\"}]",
						"""
						"elements": [
						  {"localRef": "el-a", "kind": "heading", "role": "title", "contentIntent": "welcome"},
						  {"localRef": "el-a-2", "kind": "cta", "role": "action", "contentIntent": "go",
						   "target": {"type": "page", "pageRef": "does-not-exist"}}
						]""");
		String candidateOutput = envelope("[" + brokenProposal + "," + proposal("b") + "," + proposal("c") + "]");

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.startsWith("structure[prop-a]") && issue.contains("does-not-exist"));
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsWhenARequirementRefDoesNotExistInTheActiveWebsiteRequirementsArtifact() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);

		String brokenProposal = proposal("a").replace("\"req-1\"", "\"does-not-exist\"");
		String candidateOutput = envelope("[" + brokenProposal + "," + proposal("b") + "," + proposal("c") + "]");

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues())
				.anyMatch(issue -> issue.startsWith("canonical-ref[prop-a]") && issue.contains("does-not-exist"));
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void failsWhenSemanticReviewReportsABlockingFinding() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any()))
				.thenReturn(new SemanticReviewResult(
						true, List.of(new SemanticReviewFinding("prop-a", "insufficient-differentiation", "all three look identical", true))));

		String candidateOutput = envelope(threeValidProposals());

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
		assertThat(result.validationIssues())
				.anyMatch(issue -> issue.startsWith("semantic-review[prop-a]") && issue.contains("insufficient-differentiation"));
		// The candidate itself was still recorded as audit history - only promotion was rejected.
		assertThat(candidateOutputRepository.findByAgentExecutionId(execution.getId())).hasSize(1);
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void endToEndRunFailsWhenNoCanonicalRequirementsArtifactsExistYetForTheProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> designerAgentRunner.run(project.getId()))
				.isInstanceOf(ai.architech.backend.core.error.ApplicationException.class);
	}

	private static String threeValidProposals() {
		return "[" + proposal("a") + "," + proposal("b") + "," + proposal("c") + "]";
	}

	private static String envelope(String proposalsJsonArray) {
		return "{\"design-proposal-set\": {\"proposals\": " + proposalsJsonArray + "}}";
	}

	private static String proposal(String suffix) {
		return """
				{
				  "localRef": "prop-%1$s",
				  "name": "Layout %1$s",
				  "concept": "A concept",
				  "websitePlan": {
				    "requirementRefs": ["req-1"],
				    "pages": [
				      {
				        "localRef": "page-%1$s",
				        "name": "Home",
				        "route": "/",
				        "purpose": "home",
				        "requirementRefs": ["req-1"],
				        "sections": [
				          {
				            "localRef": "sec-%1$s",
				            "kind": "hero",
				            "purpose": "intro",
				            "layoutIntent": "centered",
				            "customerDataRefs": ["cust-1"],
				            "elements": [{"localRef": "el-%1$s", "kind": "heading", "role": "title", "contentIntent": "welcome"}]
				          }
				        ]
				      }
				    ]
				  },
				  "designSpecification": {
				    "colors": [{"role": "primary", "value": "#000000"}],
				    "typography": [{"role": "heading", "fontFamily": "Inter", "fontWeight": 700, "fontSizeRem": 2, "lineHeight": 1.2}],
				    "spacing": [{"role": "section", "valueRem": 2}],
				    "layout": {"contentWidth": "standard", "density": "balanced", "pageGutterRem": 1, "sectionGapRem": 2, "gridIntent": "12-col"},
				    "uiPatterns": [],
				    "imagery": {"direction": "clean", "treatment": "flat"},
				    "responsive": {"navigationBehavior": "collapse", "contentStacking": "vertical", "typeScaling": "fluid", "spacingAdjustment": "reduce", "mediaBehavior": "scale"}
				  }
				}"""
				.formatted(suffix);
	}

	private AgentExecution startedExecution(Project project) {
		AgentExecution execution =
				agentExecutionRepository.saveAndFlush(new AgentExecution(project.getId(), "designer-agent", 1));
		execution.start();
		return agentExecutionRepository.saveAndFlush(execution);
	}
}
