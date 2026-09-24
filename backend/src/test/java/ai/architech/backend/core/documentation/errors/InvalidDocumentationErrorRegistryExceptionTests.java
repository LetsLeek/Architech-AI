package ai.architech.backend.core.documentation.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class InvalidDocumentationErrorRegistryExceptionTests {

	private final Resource resource = new ByteArrayResource(new byte[0], "test-error-registry.yaml");

	@Test
	void messageNamesTheOffendingResource() {
		InvalidDocumentationErrorRegistryException exception = new InvalidDocumentationErrorRegistryException(resource, "malformed");

		assertThat(exception.getMessage()).contains("malformed").contains("test-error-registry.yaml");
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void preservesTheOriginalCause() {
		RuntimeException cause = new RuntimeException("root cause");

		InvalidDocumentationErrorRegistryException exception = new InvalidDocumentationErrorRegistryException(resource, "malformed", cause);

		assertThat(exception.getCause()).isSameAs(cause);
	}
}
