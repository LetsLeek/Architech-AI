package ai.architech.backend.core.qa.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real-classpath proof of AIW-171's own acceptance criteria against the shipped {@code website-qa-tools@1.0.0} profile. */
@SpringBootTest
class QaToolCapabilityProfileLoaderIT {

	@Autowired
	private QaToolCapabilityProfileLoader loader;

	@Test
	void resolvesTheFrozenWebsiteQaToolsProfileByRef() {
		QaToolCapabilityProfile profile = loader.resolve("website-qa-tools@1.0.0");

		assertThat(profile.ref()).isEqualTo("website-qa-tools@1.0.0");
		assertThat(profile.capabilities())
				.containsExactlyInAnyOrder(QaToolCapability.values());
	}

	@Test
	void theFrozenProfileGrantsNoMutationOrExternalSideEffectCapability() {
		QaToolCapabilityProfile profile = loader.resolve("website-qa-tools@1.0.0");

		assertThat(profile.security().unrestrictedShell()).isFalse();
		assertThat(profile.security().arbitraryPackageInstallation()).isFalse();
		assertThat(profile.security().hostFilesystem()).isFalse();
		assertThat(profile.security().dockerSocket()).isFalse();
		assertThat(profile.security().cloudCredentials()).isFalse();
		assertThat(profile.security().deploymentCredentials()).isFalse();
		assertThat(profile.security().unrestrictedOutboundNetworking()).isFalse();
		assertThat(profile.sourceInspection().readOnly()).isTrue();
		assertThat(profile.sourceInspection().allowed()).containsExactlyInAnyOrder("list", "read", "search");
		assertThat(profile.network().rawOutbound()).isFalse();
		assertThat(profile.network().allowedOnlyThroughAuthorizedCapabilities()).isTrue();
		assertThat(profile.safeIntegrationTest().realSideEffects()).isFalse();
		assertThat(profile.safeIntegrationTest().credentialEncapsulation()).isTrue();
		assertThat(profile.browser().candidateSurfaceOnly()).isTrue();
	}

	@Test
	void supportsReportsExactlyTheGrantedCapabilities() {
		QaToolCapabilityProfile profile = loader.resolve("website-qa-tools@1.0.0");

		assertThat(profile.supports(QaToolCapability.BROWSER_AUTOMATION)).isTrue();
		assertThat(profile.supports(QaToolCapability.SOURCE_INSPECTION)).isTrue();
	}

	@Test
	void rejectsAnUnknownProfileRef() {
		assertThatThrownBy(() -> loader.resolve("website-qa-made-up@1.0.0"))
				.isInstanceOf(QaToolCapabilityProfileNotFoundException.class);
	}
}
