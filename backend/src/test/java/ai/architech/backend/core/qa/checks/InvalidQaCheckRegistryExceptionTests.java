package ai.architech.backend.core.qa.checks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class InvalidQaCheckRegistryExceptionTests {

	private final Resource resource = new ByteArrayResource(new byte[0], "test-registry.yaml");

	@Test
	void messageNamesTheOffendingResource() {
		InvalidQaCheckRegistryException exception = new InvalidQaCheckRegistryException(resource, "malformed");

		assertThat(exception.getMessage()).contains("malformed").contains("test-registry.yaml");
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void preservesTheOriginalCause() {
		RuntimeException cause = new RuntimeException("root cause");

		InvalidQaCheckRegistryException exception = new InvalidQaCheckRegistryException(resource, "malformed", cause);

		assertThat(exception.getCause()).isSameAs(cause);
	}
}
