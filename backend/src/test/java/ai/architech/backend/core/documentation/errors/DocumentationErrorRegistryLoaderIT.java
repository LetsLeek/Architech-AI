package ai.architech.backend.core.documentation.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DocumentationErrorRegistryLoaderIT {

	@Autowired
	private DocumentationErrorRegistryLoader loader;

	@Test
	void loadsAllThirtyNineFrozenErrorCodes() {
		DocumentationErrorRegistry registry = loader.load();

		assertThat(registry.registryVersion()).isEqualTo("1.0.0");
		assertThat(registry.errors()).hasSize(39);
		assertThat(registry.notes()).hasSize(2);
	}

	@Test
	void resolvesAKnownBlockingAuthorityErrorCode() {
		DocumentationErrorCode code = loader.load().byCode("MISSING_REQUIRED_AUTHORITY").orElseThrow();

		assertThat(code.issueClass()).isEqualTo("AUTHORITY_ISSUE");
		assertThat(code.defaultRemediationTarget()).isEqualTo("UPSTREAM_AUTHORITY");
		assertThat(code.retryPolicy()).isEqualTo("WAIT_FOR_UPSTREAM");
		assertThat(code.blocking()).isTrue();
	}

	@Test
	void doesNotResolveAnUnknownErrorCode() {
		assertThat(loader.load().byCode("DOES_NOT_EXIST")).isEmpty();
	}
}
