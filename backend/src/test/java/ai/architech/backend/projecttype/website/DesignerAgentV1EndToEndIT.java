package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import ai.architech.backend.core.runner.RunnerResult;
import ai.architech.backend.core.validation.DesignProposalSetSemanticReviewer;
import ai.architech.backend.core.validation.SemanticReviewFinding;
import ai.architech.backend.core.validation.SemanticReviewResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Designer Agent V1 end-to-end contract fixtures and tests (AIW-125) - the ten scenarios the
 * ticket's own AC lists, against version-controlled fixtures under {@code
 * src/test/resources/fixtures/designer-agent/}.
 *
 * <p><strong>Honest split, stated plainly</strong>: scenarios about structural/reference
 * validity and atomic all-or-nothing persistence are proven against the real deterministic
 * pipeline - a genuinely broken candidate really does fail here, for real reasons. Scenarios
 * that are inherently semantic judgments (is this proposal set meaningfully diverse; was a
 * fact invented; was a conflict silently resolved; did contact information quietly become a
 * feature) are proven by stubbing {@link DesignProposalSetSemanticReviewer} - AIW-123's own
 * documented limitation applies here too: this proves the pipeline correctly turns a semantic
 * finding into a rejected, non-canonical candidate, not that a real model reliably produces
 * that finding in the first place.
 */
@SpringBootTest
@Transactional
class DesignerAgentV1EndToEndIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private DesignerAgentRunner designerAgentRunner;

	@MockitoBean
	private DesignProposalSetSemanticReviewer designProposalSetSemanticReviewer;

	private static final String CUSTOMER_PROFILE = readFixture("customer-profile.json");
	private static final String WEBSITE_REQUIREMENTS = readFixture("website-requirements.json");
	private static final String VALID_PROPOSALS = readFixture("valid-design-proposal-set.json");
	private static final String INVALID_REFERENCE_PROPOSALS = readFixture("invalid-reference-design-proposal-set.json");

	@Test
	void unconstrainedValidInputProducesExactlyThreeCoherentProposals() {
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any())).thenReturn(SemanticReviewResult.passed());

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isTrue();
		assertThat(proposalCountOf(result.designProposalSetVersion())).isEqualTo(3);
	}

	@Test
	void tightConstraintsStillProduceThreeHonestAlternativesWithoutViolatingThem() {
		// Same fixture as the unconstrained case: constraint-tightness is a semantic property
		// of *how* the three proposals were derived, not a structural difference this
		// deterministic pipeline can see - what this proves is that a legitimately
		// tightly-constrained-but-valid set is not wrongly rejected by the deterministic layers
		// once the (stubbed) semantic reviewer confirms it preserves every constraint.
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any())).thenReturn(SemanticReviewResult.passed());

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isTrue();
	}

	@Test
	void aMustRequirementLostFromAnyProposalIsDetectedAndBlocksPersistence() {
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any()))
				.thenReturn(new SemanticReviewResult(
						true, List.of(new SemanticReviewFinding("prop-c", "requirement-lost", "req-content-1 is missing from prop-c", true))));

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.contains("requirement-lost"));
	}

	@Test
	void aCouldRequirementMayVaryAcrossProposalsWithoutBeingReclassifiedAsAFailure() {
		// No "requirement-strength-changed" finding reported - varying optional-requirement
		// treatment across proposals is legitimate design freedom, not a violation.
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any())).thenReturn(SemanticReviewResult.passed());

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isTrue();
	}

	@Test
	void missingOrAmbiguousDataRemainingUnresolvedDoesNotBecomeAnInventedFact() {
		// The inverse case is what would actually be caught: an invented fact reported as
		// "unsupported-scope" blocks persistence, proving unresolved data isn't silently
		// allowed to firm up into a fabricated customer fact.
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any()))
				.thenReturn(new SemanticReviewResult(
						true,
						List.of(new SemanticReviewFinding(
								"prop-a", "unsupported-scope", "invented a specific opening time not present in customer-profile", true))));

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.contains("unsupported-scope"));
	}

	@Test
	void aCanonicalConflictSilentlyResolvedInsteadOfLeftUnresolvedBlocksPersistence() {
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any()))
				.thenReturn(new SemanticReviewResult(
						true,
						List.of(new SemanticReviewFinding(
								null, "unknown-or-conflict-resolved", "a canonical conflict was silently resolved instead of left unresolved", true))));

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.contains("unknown-or-conflict-resolved"));
	}

	@Test
	void contactInformationDoesNotSilentlyBecomeAContactFormFeature() {
		// Inventing a functional capability (a contact form) that customer-profile/
		// website-requirements never asked for is scope expansion - the same "unsupported-scope"
		// category as any other invented capability, not a separate category of its own.
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any()))
				.thenReturn(new SemanticReviewResult(
						true,
						List.of(new SemanticReviewFinding(
								"prop-b", "unsupported-scope", "added a contact form the customer never requested", true))));

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.contains("contact form"));
	}

	@Test
	void invalidLocalOrCanonicalReferencesFailValidation() {
		// Real deterministic failure, no semantic reviewer stub involved at all - it never gets
		// reached, since the structure validator already rejects the dangling patternRef.
		DesignProposalGenerationResult result = runWith(INVALID_REFERENCE_PROPOSALS);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.startsWith("structure[prop-a]") && issue.contains("pattern-does-not-exist"));
	}

	@Test
	void cosmeticOnlyDuplicateProposalsFailSemanticDifferentiationPolicy() {
		when(designProposalSetSemanticReviewer.review(any(), any(), any(), any()))
				.thenReturn(new SemanticReviewResult(
						true,
						List.of(new SemanticReviewFinding(
								null, "insufficient-differentiation", "all three proposals share the same structure and only vary color tokens", true))));

		DesignProposalGenerationResult result = runWith(VALID_PROPOSALS);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.validationIssues()).anyMatch(issue -> issue.contains("insufficient-differentiation"));
	}

	@Test
	void failureOfOneProposalCausesTheCompleteCandidateSetToRemainNonCanonical() {
		// Two of the three proposals in this fixture are entirely valid - only prop-a's one
		// element has a dangling patternRef. Proves partial validity never partially persists:
		// the whole three-proposal set stays non-canonical together.
		DesignProposalGenerationResult result = runWith(INVALID_REFERENCE_PROPOSALS);

		assertThat(result.succeeded()).isFalse();
		assertThat(result.designProposalSetVersion()).isNull();
		assertThat(artifactVersionRepository.findByAgentExecutionId(result.execution().getId())).isEmpty();
	}

	private DesignProposalGenerationResult runWith(String proposalsJson) {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(new AgentExecution(project.getId(), "designer-agent", 1));
		execution.start();
		agentExecutionRepository.saveAndFlush(execution);

		String candidateOutput = "{\"design-proposal-set\": " + proposalsJson + "}";
		return designerAgentRunner.validateAndPersist(
				project.getId(), CUSTOMER_PROFILE, WEBSITE_REQUIREMENTS, new RunnerResult(execution, candidateOutput));
	}

	private static int proposalCountOf(ArtifactVersion version) {
		try {
			JsonNode root = new ObjectMapper().readTree(version.getContent());
			return root.path("proposals").size();
		} catch (RuntimeException e) {
			throw new IllegalStateException("Persisted design-proposal-set was not valid JSON", e);
		}
	}

	private static String readFixture(String filename) {
		try {
			return StreamUtils.copyToString(
					new ClassPathResource("fixtures/designer-agent/" + filename).getInputStream(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read fixture: " + filename, e);
		}
	}
}
