package ai.architech.backend.core.qa.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FindingDeduplicationValidatorTests {

	private final FindingDeduplicationValidator validator = new FindingDeduplicationValidator();

	@Test
	void anExactFingerprintMatchIsADuplicate() {
		assertThat(validator.isDuplicate("fp-1", List.of("fp-1", "fp-2"))).isTrue();
	}

	@Test
	void aNewFingerprintIsNotADuplicate() {
		assertThat(validator.isDuplicate("fp-3", List.of("fp-1", "fp-2"))).isFalse();
	}

	@Test
	void neverOverMergesDistinctFingerprintsEvenWithNoExisting() {
		assertThat(validator.isDuplicate("fp-1", List.of())).isFalse();
	}
}
