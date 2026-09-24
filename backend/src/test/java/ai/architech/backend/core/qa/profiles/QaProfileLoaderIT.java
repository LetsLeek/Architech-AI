package ai.architech.backend.core.qa.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.qa.invariants.QaSeverity;
import ai.architech.backend.core.qa.policy.PolicyDisposition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real-classpath proof of AIW-175's own "Both frozen profile YAMLs are loaded/versioned" and
 * "Comparison uses required Wide/Narrow contexts; Full Release uses Wide/Medium/Narrow contexts",
 * plus the "cannot be silently substituted for one another" guarantee.
 */
@SpringBootTest
class QaProfileLoaderIT {

	@Autowired
	private QaProfileLoader loader;

	@Test
	void resolvesTheComparisonReadinessProfileWithItsOwnTwoViewports() {
		QaProfile profile = loader.resolve("website-qa-comparison-readiness@1.0.0");

		assertThat(profile.ref()).isEqualTo("website-qa-comparison-readiness@1.0.0");
		assertThat(profile.profileType()).isEqualTo(QaProfileType.COMPARISON_READINESS);
		assertThat(profile.viewports()).extracting(QaViewport::id).containsExactlyInAnyOrder("COMPARISON_WIDE", "COMPARISON_NARROW");
	}

	@Test
	void resolvesTheFullReleaseProfileWithItsOwnThreeViewports() {
		QaProfile profile = loader.resolve("website-qa-full-release@1.0.0");

		assertThat(profile.ref()).isEqualTo("website-qa-full-release@1.0.0");
		assertThat(profile.profileType()).isEqualTo(QaProfileType.FULL_RELEASE);
		assertThat(profile.viewports()).extracting(QaViewport::id)
				.containsExactlyInAnyOrder("FULL_WIDE", "FULL_MEDIUM", "FULL_NARROW");
	}

	@Test
	void comparisonReadinessNeverResolvesToTheFullReleaseProfileOrViceVersa() {
		QaProfile comparison = loader.resolve("website-qa-comparison-readiness@1.0.0");
		QaProfile fullRelease = loader.resolve("website-qa-full-release@1.0.0");

		assertThat(comparison.profileType()).isNotEqualTo(fullRelease.profileType());
		assertThat(comparison.ref()).isNotEqualTo(fullRelease.ref());
	}

	@Test
	void comparisonReadinessLeavesSeoAndPerformanceNotApplicableWhileFullReleaseRequiresThem() {
		QaProfile comparison = loader.resolve("website-qa-comparison-readiness@1.0.0");
		QaProfile fullRelease = loader.resolve("website-qa-full-release@1.0.0");

		assertThat(comparison.domain("SEO_METADATA_BASELINE").orElseThrow().applicability())
				.isEqualTo(QaDomainApplicability.NOT_APPLICABLE);
		assertThat(comparison.domain("PERFORMANCE_BASELINE").orElseThrow().applicability())
				.isEqualTo(QaDomainApplicability.NOT_APPLICABLE);
		assertThat(fullRelease.domain("SEO_METADATA_BASELINE").orElseThrow().applicability())
				.isEqualTo(QaDomainApplicability.REQUIRED);
		assertThat(fullRelease.domain("PERFORMANCE_BASELINE").orElseThrow().applicability())
				.isEqualTo(QaDomainApplicability.REQUIRED);
	}

	@Test
	void theTwoProfilesUseDistinctIntegrationBehaviorConditionKeys() {
		QaProfile comparison = loader.resolve("website-qa-comparison-readiness@1.0.0");
		QaProfile fullRelease = loader.resolve("website-qa-full-release@1.0.0");

		assertThat(comparison.domain("INTEGRATION_BEHAVIOR").orElseThrow().conditionKey())
				.isEqualTo("AUTHORIZED_BOUND_CAPABILITY_MATERIALLY_RELEVANT_TO_COMPARISON");
		assertThat(fullRelease.domain("INTEGRATION_BEHAVIOR").orElseThrow().conditionKey())
				.isEqualTo("AUTHORIZED_BOUND_CAPABILITY_APPLICABLE");
	}

	@Test
	void bothProfilesDeclareTheSameFourPreconditionChecks() {
		QaProfile comparison = loader.resolve("website-qa-comparison-readiness@1.0.0");

		assertThat(comparison.preconditionChecks()).containsExactlyInAnyOrder(
				"CANDIDATE_VERIFICATION_PROVENANCE", "CANDIDATE_SOURCE_IDENTITY",
				"EXECUTION_SURFACE_CANDIDATE_BINDING", "AUTHORITY_REFERENCE_INTEGRITY");
	}

	@Test
	void fullReleaseDeclaresItsOwnExplicitCodeRulesSeverityDefaultsAndRequirementPolicy() {
		QaProfile fullRelease = loader.resolve("website-qa-full-release@1.0.0");

		assertThat(fullRelease.findingDispositionPolicy().explicitCodeRules())
				.containsEntry("CONTENT_PLACEHOLDER_LEAK", PolicyDisposition.BLOCK)
				.containsEntry("SEO_REQUIRED_TITLE_MISSING", PolicyDisposition.BLOCK);
		assertThat(fullRelease.findingDispositionPolicy().severityDefaults())
				.containsEntry(QaSeverity.CRITICAL, PolicyDisposition.BLOCK)
				.containsEntry(QaSeverity.MAJOR, PolicyDisposition.BLOCK)
				.containsEntry(QaSeverity.MINOR, PolicyDisposition.ALLOW);
		assertThat(fullRelease.requirementPolicy()).isPresent();
		assertThat(fullRelease.requirementPolicy().get().materiallyUnfulfilledMust()).isEqualTo(PolicyDisposition.BLOCK);
		assertThat(fullRelease.authorityIssuePolicy().gateRelevantIssueDisposition()).isEqualTo(PolicyDisposition.ESCALATE);
		assertThat(fullRelease.evaluationIssuePolicy().requiredEvaluationDisposition()).isEqualTo(PolicyDisposition.ESCALATE);
	}

	@Test
	void comparisonReadinessDeclaresNoExplicitCodeRulesAndNoRequirementPolicy() {
		QaProfile comparison = loader.resolve("website-qa-comparison-readiness@1.0.0");

		assertThat(comparison.findingDispositionPolicy().explicitCodeRules()).isEmpty();
		assertThat(comparison.findingDispositionPolicy().severityDefaults())
				.containsEntry(QaSeverity.MAJOR, PolicyDisposition.BLOCK)
				.containsEntry(QaSeverity.MINOR, PolicyDisposition.ALLOW);
		assertThat(comparison.requirementPolicy()).isEmpty();
	}

	@Test
	void rejectsAnUnknownProfileRef() {
		assertThatThrownBy(() -> loader.resolve("website-qa-made-up@1.0.0")).isInstanceOf(QaProfileNotFoundException.class);
	}
}
