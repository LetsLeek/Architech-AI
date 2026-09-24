package ai.architech.backend.core.documentation.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class InvalidLocaleRegistryExceptionTests {

	private final Resource resource = new ByteArrayResource(new byte[0], "test-locale-registry.yaml");

	@Test
	void messageNamesTheOffendingResource() {
		InvalidLocaleRegistryException exception = new InvalidLocaleRegistryException(resource, "malformed");

		assertThat(exception.getMessage()).contains("malformed").contains("test-locale-registry.yaml");
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void preservesTheOriginalCause() {
		RuntimeException cause = new RuntimeException("root cause");

		InvalidLocaleRegistryException exception = new InvalidLocaleRegistryException(resource, "malformed", cause);

		assertThat(exception.getCause()).isSameAs(cause);
	}
}
