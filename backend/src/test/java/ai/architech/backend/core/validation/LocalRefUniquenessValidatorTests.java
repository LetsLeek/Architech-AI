package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalRefUniquenessValidatorTests {

	private final LocalRefUniquenessValidator validator = new LocalRefUniquenessValidator();

	@Test
	void acceptsAllDistinctLocalRefs() {
		LocalRefValidationResult result = validator.validate(List.of("loc-1", "loc-2", "loc-3"));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsADuplicatedLocalRef() {
		LocalRefValidationResult result = validator.validate(List.of("loc-1", "loc-2", "loc-1"));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).extracting(LocalRefValidationIssue::localRef).containsExactly("loc-1");
	}

	@Test
	void acceptsAnEmptyList() {
		assertThat(validator.validate(List.of()).valid()).isTrue();
	}
}
