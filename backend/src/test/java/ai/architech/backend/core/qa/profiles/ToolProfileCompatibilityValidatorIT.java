package ai.architech.backend.core.qa.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.qa.tooling.QaToolCapability;
import ai.architech.backend.core.qa.tooling.QaToolCapabilityProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real-registry proof that a genuinely incompatible tool profile is detected before execution (AIW-175). */
@SpringBootTest
class ToolProfileCompatibilityValidatorIT {

	@Autowired
	private ToolProfileCompatibilityValidator validator;

	@Test
	void detectsAMissingRequiredCapabilityBeforeExecution() {
		QaToolCapabilityProfile restrictedProfile = new QaToolCapabilityProfile(
				"restricted-tools@1.0.0",
				List.of(QaToolCapability.METADATA_INSPECTION),
				new QaToolCapabilityProfile.Browser(true),
				new QaToolCapabilityProfile.SourceInspection(List.of("list", "read", "search"), true),
				new QaToolCapabilityProfile.Network(false, true, List.of()),
				new QaToolCapabilityProfile.SafeIntegrationTest(false, true),
				new QaToolCapabilityProfile.Security(false, false, false, false, false, false, false));

		ResolvedDomainApplicabilityResult navigation =
				new ResolvedDomainApplicabilityResult("RUNTIME_BROWSER", ResolvedDomainApplicability.REQUIRED, List.of("CANONICAL_ROUTE_REACHABILITY"));

		ToolProfileCompatibilityResult result = validator.validate(restrictedProfile, List.of(navigation));

		assertThat(result.passed()).isFalse();
		assertThat(result.problems()).containsExactly(
				new ToolProfileCompatibilityProblem("CANONICAL_ROUTE_REACHABILITY", QaToolCapability.BROWSER_AUTOMATION));
	}

	@Test
	void ignoresDomainsThatAreNotCurrentlyInScope() {
		QaToolCapabilityProfile restrictedProfile = new QaToolCapabilityProfile(
				"restricted-tools@1.0.0",
				List.of(),
				new QaToolCapabilityProfile.Browser(true),
				new QaToolCapabilityProfile.SourceInspection(List.of("list", "read", "search"), true),
				new QaToolCapabilityProfile.Network(false, true, List.of()),
				new QaToolCapabilityProfile.SafeIntegrationTest(false, true),
				new QaToolCapabilityProfile.Security(false, false, false, false, false, false, false));

		ResolvedDomainApplicabilityResult notApplicable =
				new ResolvedDomainApplicabilityResult("RUNTIME_BROWSER", ResolvedDomainApplicability.NOT_APPLICABLE, List.of());
		ResolvedDomainApplicabilityResult missingAuthority = new ResolvedDomainApplicabilityResult(
				"INTEGRATION_BEHAVIOR", ResolvedDomainApplicability.MISSING_REQUIRED_AUTHORITY, List.of("INTEGRATION_CONTRACT_REFERENCE_INTEGRITY"));

		ToolProfileCompatibilityResult result = validator.validate(restrictedProfile, List.of(notApplicable, missingAuthority));

		assertThat(result.passed()).isTrue();
	}
}
