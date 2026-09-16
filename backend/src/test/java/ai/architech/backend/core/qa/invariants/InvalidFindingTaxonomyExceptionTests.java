package ai.architech.backend.core.qa.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class InvalidFindingTaxonomyExceptionTests {

	private final Resource resource = new ByteArrayResource(new byte[0], "test-taxonomy.yaml");

	@Test
	void messageNamesTheOffendingResource() {
		InvalidFindingTaxonomyException exception = new InvalidFindingTaxonomyException(resource, "malformed");

		assertThat(exception.getMessage()).contains("malformed").contains("test-taxonomy.yaml");
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void preservesTheOriginalCause() {
		RuntimeException cause = new RuntimeException("root cause");

		InvalidFindingTaxonomyException exception = new InvalidFindingTaxonomyException(resource, "malformed", cause);

		assertThat(exception.getCause()).isSameAs(cause);
	}
}
