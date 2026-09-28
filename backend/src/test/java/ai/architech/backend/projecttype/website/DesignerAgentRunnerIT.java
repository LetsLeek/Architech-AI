package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.agentexecution.AgentExecutionStatus;
import ai.architech.backend.core.artifact.CandidateOutput;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.artifact.CandidatePromoter;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.runner.AgentRunner;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.DesignProposalSetSemanticReviewer;
import ai.architech.backend.core.validation.SemanticReviewFinding;
import ai.architech.backend.core.validation.SemanticReviewResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

	@Autowired
	private CandidatePromoter candidatePromoter;

	@MockitoBean
	private DesignProposalSetSemanticReviewer designProposalSetSemanticReviewer;

	// AIW-216: mocked so the feedback-retry-loop tests below can hand-script exactly what each
	// attempt (first, then correction) returns, without needing a real AI provider - same
	// reasoning DesignProposalSetSemanticReviewer above is already mocked for. BoundedRetryAgentRunner
	// itself stays real: it's a thin wrapper that just delegates its single call straight through
	// to this same mock, so wiring it doesn't add any behavior worth faking separately.
	@MockitoBean
	private AgentRunner agentRunner;

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
	void passesSchemaValidationWhenASectionHasAnExplicitNullCustomKindOnANonCustomKind() {
		// AIW-215: the real model (Claude Sonnet 5) emitted "customKind": null on a section whose
		// kind was not "custom" - the schema's if/then/else requires customKind to be completely
		// absent (not merely falsy) whenever kind != "custom", so a present-but-null value used to
		// fail schema validation even though it's a harmless JSON-encoding quirk.
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any())).thenReturn(SemanticReviewResult.passed());

		String proposalWithExplicitNullCustomKind =
				proposal("a").replace("\"kind\": \"hero\",", "\"kind\": \"hero\", \"customKind\": null,");
		String candidateOutput =
				envelope("[" + proposalWithExplicitNullCustomKind + "," + proposal("b") + "," + proposal("c") + "]");

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isTrue();
		assertThat(result.validationIssues()).isEmpty();
		assertThat(result.execution().getStatus()).isEqualTo(AgentExecutionStatus.SUCCEEDED);
	}

	@Test
	void stillFailsSchemaValidationWhenAGenuinelyRequiredFieldIsMissing() {
		// Proves the null-stripping normalization doesn't mask real schema violations: dropping the
		// required "kind" property entirely (not merely setting it to null) must still fail.
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = startedExecution(project);

		String proposalMissingRequiredKind = proposal("a").replace("\"kind\": \"hero\",", "");
		String candidateOutput =
				envelope("[" + proposalMissingRequiredKind + "," + proposal("b") + "," + proposal("c") + "]");

		DesignProposalGenerationResult result = designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.startsWith("schema:"));
	}

	@Test
	void endToEndRunFailsWhenNoCanonicalRequirementsArtifactsExistYetForTheProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> designerAgentRunner.run(project.getId()))
				.isInstanceOf(ai.architech.backend.core.error.ApplicationException.class);
	}

	// AIW-216: DesignerAgentRunner#run's own bounded feedback-correction retry loop. AgentRunner
	// is mocked (see the field above) so each attempt's raw model output is exactly what this test
	// scripts, without needing a real AI provider - every real validator/persistence step below
	// still runs for real, same as every other test in this class.

	@Test
	void runSucceedsOnFirstAttemptWithoutAnyFeedbackRetry() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		promoteCanonicalArtifact(project.getId(), DesignerAgentRunner.CUSTOMER_PROFILE_TYPE, CUSTOMER_PROFILE);
		promoteCanonicalArtifact(project.getId(), DesignerAgentRunner.WEBSITE_REQUIREMENTS_TYPE, WEBSITE_REQUIREMENTS);
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any())).thenReturn(SemanticReviewResult.passed());

		AgentExecution firstAttemptExecution = startedExecution(project);
		when(agentRunner.runWithInputArtifacts(
						eq(project.getId()), eq(DesignerAgentRunner.AGENT_ID), eq(DesignerAgentRunner.AGENT_VERSION), any()))
				.thenReturn(new RunnerResult(firstAttemptExecution, envelope(threeValidProposals())));

		DesignProposalGenerationResult result = designerAgentRunner.run(project.getId());

		assertThat(result.succeeded()).isTrue();
		assertThat(result.validationIssues()).isEmpty();
		assertThat(result.execution().getId()).isEqualTo(firstAttemptExecution.getId());
		verify(agentRunner, never()).runWithInputArtifactsAndCorrection(any(), any(), anyInt(), any(), any(), any());
	}

	@Test
	void runIssuesOneFeedbackRetryWithTheRealValidationIssuesAndSucceeds() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		promoteCanonicalArtifact(project.getId(), DesignerAgentRunner.CUSTOMER_PROFILE_TYPE, CUSTOMER_PROFILE);
		promoteCanonicalArtifact(project.getId(), DesignerAgentRunner.WEBSITE_REQUIREMENTS_TYPE, WEBSITE_REQUIREMENTS);
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any())).thenReturn(SemanticReviewResult.passed());

		// Only 2 proposals instead of the schema-required 3 - same fixture
		// failsButStillRecordsTheCandidateAsAuditWhenSchemaValidationFails above already proves
		// fails real schema validation with a genuine "schema:" issue.
		String invalidCandidateOutput = envelope("[" + proposal("a") + "," + proposal("b") + "]");
		AgentExecution firstAttemptExecution = startedExecution(project);
		when(agentRunner.runWithInputArtifacts(
						eq(project.getId()), eq(DesignerAgentRunner.AGENT_ID), eq(DesignerAgentRunner.AGENT_VERSION), any()))
				.thenReturn(new RunnerResult(firstAttemptExecution, invalidCandidateOutput));

		// Computes the exact real issues the pipeline produces for this invalid candidate against a
		// throwaway execution, so the assertion below is tied to genuine validator output - not a
		// hardcoded guess at its wording.
		List<String> realIssuesFromFirstAttempt = designerAgentRunner
				.validateAndPersist(
						project.getId(),
						CUSTOMER_PROFILE,
						WEBSITE_REQUIREMENTS,
						new RunnerResult(startedExecution(project), invalidCandidateOutput))
				.validationIssues();
		assertThat(realIssuesFromFirstAttempt).isNotEmpty();

		AgentExecution retryExecution = startedExecution(project);
		ArgumentCaptor<String> feedbackCaptor = ArgumentCaptor.forClass(String.class);
		when(agentRunner.runWithInputArtifactsAndCorrection(
						eq(project.getId()),
						eq(DesignerAgentRunner.AGENT_ID),
						eq(DesignerAgentRunner.AGENT_VERSION),
						any(),
						eq(invalidCandidateOutput),
						feedbackCaptor.capture()))
				.thenReturn(new RunnerResult(retryExecution, envelope(threeValidProposals())));

		DesignProposalGenerationResult result = designerAgentRunner.run(project.getId());

		assertThat(result.succeeded()).isTrue();
		assertThat(result.execution().getId()).isEqualTo(retryExecution.getId());
		verify(agentRunner, times(1)).runWithInputArtifactsAndCorrection(any(), any(), anyInt(), any(), any(), any());
		realIssuesFromFirstAttempt.forEach(issue -> assertThat(feedbackCaptor.getValue()).contains(issue));
	}

	@Test
	void runReturnsTheLastAttemptsIssuesAfterExhaustingTheFeedbackRetryBudgetWithNoThirdAttempt() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		promoteCanonicalArtifact(project.getId(), DesignerAgentRunner.CUSTOMER_PROFILE_TYPE, CUSTOMER_PROFILE);
		promoteCanonicalArtifact(project.getId(), DesignerAgentRunner.WEBSITE_REQUIREMENTS_TYPE, WEBSITE_REQUIREMENTS);

		// First attempt: only 2 proposals -> fails with a "schema:" issue.
		String firstInvalidCandidateOutput = envelope("[" + proposal("a") + "," + proposal("b") + "]");
		AgentExecution firstAttemptExecution = startedExecution(project);
		when(agentRunner.runWithInputArtifacts(
						eq(project.getId()), eq(DesignerAgentRunner.AGENT_ID), eq(DesignerAgentRunner.AGENT_VERSION), any()))
				.thenReturn(new RunnerResult(firstAttemptExecution, firstInvalidCandidateOutput));

		// Retry: 3 proposals (passes schema) but one page ref doesn't resolve within its own
		// proposal -> a distinct "structure:" issue, proving the final result carries the RETRY's
		// own issues, not the first attempt's.
		String brokenProposal = proposal("a")
				.replace(
						"\"elements\": [{\"localRef\": \"el-a\", \"kind\": \"heading\", \"role\": \"title\", \"contentIntent\": \"welcome\"}]",
						"""
						"elements": [
						  {"localRef": "el-a", "kind": "heading", "role": "title", "contentIntent": "welcome"},
						  {"localRef": "el-a-2", "kind": "cta", "role": "action", "contentIntent": "go",
						   "target": {"type": "page", "pageRef": "does-not-exist"}}
						]""");
		String retryInvalidCandidateOutput = envelope("[" + brokenProposal + "," + proposal("b") + "," + proposal("c") + "]");
		AgentExecution retryExecution = startedExecution(project);
		when(agentRunner.runWithInputArtifactsAndCorrection(
						eq(project.getId()),
						eq(DesignerAgentRunner.AGENT_ID),
						eq(DesignerAgentRunner.AGENT_VERSION),
						any(),
						eq(firstInvalidCandidateOutput),
						any()))
				.thenReturn(new RunnerResult(retryExecution, retryInvalidCandidateOutput));

		DesignProposalGenerationResult result = designerAgentRunner.run(project.getId());

		assertThat(result.succeeded()).isFalse();
		assertThat(result.execution().getId()).isEqualTo(retryExecution.getId());
		assertThat(result.validationIssues())
				.anyMatch(issue -> issue.startsWith("structure[prop-a]") && issue.contains("does-not-exist"));
		assertThat(result.validationIssues()).noneMatch(issue -> issue.startsWith("schema:"));
		// max-feedback-retries defaults to 1 (application.yml) - exactly one retry, never a third
		// attempt.
		verify(agentRunner, times(1)).runWithInputArtifactsAndCorrection(any(), any(), anyInt(), any(), any(), any());
	}

	private void promoteCanonicalArtifact(UUID projectId, String type, String content) {
		AgentExecution seedExecution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		CandidateOutput candidate = candidateOutputRepository.saveAndFlush(new CandidateOutput(seedExecution.getId(), type, content));
		candidatePromoter.promote(projectId, candidate);
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
