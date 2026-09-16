package ai.architech.backend.core.qa.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class InvalidQaToolCapabilityProfileExceptionTests {

	private final Resource resource = new ByteArrayResource(new byte[0], "test-profile.yaml");

	@Test
	void messageNamesTheOffendingResource() {
		InvalidQaToolCapabilityProfileException exception = new InvalidQaToolCapabilityProfileException(resource, "malformed");

		assertThat(exception.getMessage()).contains("malformed").contains("test-profile.yaml");
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void preservesTheOriginalCause() {
		RuntimeException cause = new RuntimeException("root cause");

		InvalidQaToolCapabilityProfileException exception = new InvalidQaToolCapabilityProfileException(resource, "malformed", cause);

		assertThat(exception.getCause()).isSameAs(cause);
	}
}
