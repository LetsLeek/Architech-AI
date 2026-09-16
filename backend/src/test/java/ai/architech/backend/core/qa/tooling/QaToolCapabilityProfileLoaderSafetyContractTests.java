package ai.architech.backend.core.qa.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Representative security/isolation tests for AIW-171's own safety contract - hand-constructs
 * {@link QaToolCapabilityProfile} instances with individual fields deliberately relaxed and
 * proves {@link QaToolCapabilityProfileLoader#safetyContractViolations} actually catches each
 * one, independent of the real shipped YAML (which {@link QaToolCapabilityProfileLoaderIT}
 * separately proves is itself compliant).
 */
class QaToolCapabilityProfileLoaderSafetyContractTests {

	@Test
	void aFullyCompliantProfileHasNoViolations() {
		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(compliantProfile())).isEmpty();
	}

	@Test
	void detectsAnUnrestrictedShellGrant() {
		QaToolCapabilityProfile profile = withSecurity(compliantProfile(),
				new QaToolCapabilityProfile.Security(true, false, false, false, false, false, false));

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile)).contains("unrestrictedShell");
	}

	@Test
	void detectsAHostFilesystemGrant() {
		QaToolCapabilityProfile profile = withSecurity(compliantProfile(),
				new QaToolCapabilityProfile.Security(false, false, true, false, false, false, false));

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile)).contains("hostFilesystem");
	}

	@Test
	void detectsADockerSocketGrant() {
		QaToolCapabilityProfile profile = withSecurity(compliantProfile(),
				new QaToolCapabilityProfile.Security(false, false, false, true, false, false, false));

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile)).contains("dockerSocket");
	}

	@Test
	void detectsCloudAndDeploymentCredentialGrants() {
		QaToolCapabilityProfile profile = withSecurity(compliantProfile(),
				new QaToolCapabilityProfile.Security(false, false, false, false, true, true, false));

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile))
				.contains("cloudCredentials", "deploymentCredentials");
	}

	@Test
	void detectsUnrestrictedOutboundNetworking() {
		QaToolCapabilityProfile profile = withSecurity(compliantProfile(),
				new QaToolCapabilityProfile.Security(false, false, false, false, false, false, true));

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile)).contains("unrestrictedOutboundNetworking");
	}

	@Test
	void detectsSourceInspectionThatIsNotReadOnly() {
		QaToolCapabilityProfile compliant = compliantProfile();
		QaToolCapabilityProfile profile = new QaToolCapabilityProfile(
				compliant.ref(),
				compliant.capabilities(),
				compliant.browser(),
				new QaToolCapabilityProfile.SourceInspection(List.of("list", "read", "write"), false),
				compliant.network(),
				compliant.safeIntegrationTest(),
				compliant.security());

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile))
				.anyMatch(v -> v.contains("sourceInspection.readOnly"));
	}

	@Test
	void detectsRawOutboundNetworkAccess() {
		QaToolCapabilityProfile compliant = compliantProfile();
		QaToolCapabilityProfile profile = new QaToolCapabilityProfile(
				compliant.ref(),
				compliant.capabilities(),
				compliant.browser(),
				compliant.sourceInspection(),
				new QaToolCapabilityProfile.Network(true, true, List.of("candidate-execution-surface")),
				compliant.safeIntegrationTest(),
				compliant.security());

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile))
				.anyMatch(v -> v.contains("network.rawOutbound"));
	}

	@Test
	void detectsRealExternalSideEffectsAllowedByDefault() {
		QaToolCapabilityProfile compliant = compliantProfile();
		QaToolCapabilityProfile profile = new QaToolCapabilityProfile(
				compliant.ref(),
				compliant.capabilities(),
				compliant.browser(),
				compliant.sourceInspection(),
				compliant.network(),
				new QaToolCapabilityProfile.SafeIntegrationTest(true, true),
				compliant.security());

		assertThat(QaToolCapabilityProfileLoader.safetyContractViolations(profile))
				.anyMatch(v -> v.contains("safeIntegrationTest.realSideEffects"));
	}

	private static QaToolCapabilityProfile withSecurity(QaToolCapabilityProfile profile, QaToolCapabilityProfile.Security security) {
		return new QaToolCapabilityProfile(
				profile.ref(),
				profile.capabilities(),
				profile.browser(),
				profile.sourceInspection(),
				profile.network(),
				profile.safeIntegrationTest(),
				security);
	}

	private static QaToolCapabilityProfile compliantProfile() {
		return new QaToolCapabilityProfile(
				"website-qa-tools@1.0.0",
				List.of(QaToolCapability.BROWSER_AUTOMATION, QaToolCapability.SOURCE_INSPECTION),
				new QaToolCapabilityProfile.Browser(true),
				new QaToolCapabilityProfile.SourceInspection(List.of("list", "read", "search"), true),
				new QaToolCapabilityProfile.Network(false, true, List.of("candidate-execution-surface")),
				new QaToolCapabilityProfile.SafeIntegrationTest(false, true),
				new QaToolCapabilityProfile.Security(false, false, false, false, false, false, false));
	}
}
