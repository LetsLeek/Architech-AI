package ai.architech.backend.core.qa.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QaRemediationPropertiesTests {

	@Test
	void acceptsANonNegativeBound() {
		QaRemediationProperties properties = new QaRemediationProperties(2);

		assertThat(properties.maxRemediationCyclesPerStage()).isEqualTo(2);
	}

	@Test
	void rejectsANegativeBound() {
		assertThatThrownBy(() -> new QaRemediationProperties(-1)).isInstanceOf(IllegalStateException.class);
	}
}
