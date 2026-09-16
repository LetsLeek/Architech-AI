package ai.architech.backend.core.qa.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QaToolCapabilityProfileTests {

	@Test
	void supportsReturnsFalseForACapabilityNotInTheGrantedSet() {
		QaToolCapabilityProfile profile = new QaToolCapabilityProfile(
				"website-qa-tools@1.0.0",
				List.of(QaToolCapability.BROWSER_AUTOMATION),
				new QaToolCapabilityProfile.Browser(true),
				new QaToolCapabilityProfile.SourceInspection(List.of("list", "read", "search"), true),
				new QaToolCapabilityProfile.Network(false, true, List.of("candidate-execution-surface")),
				new QaToolCapabilityProfile.SafeIntegrationTest(false, true),
				new QaToolCapabilityProfile.Security(false, false, false, false, false, false, false));

		assertThat(profile.supports(QaToolCapability.BROWSER_AUTOMATION)).isTrue();
		assertThat(profile.supports(QaToolCapability.SAFE_INTEGRATION_TEST)).isFalse();
	}
}
