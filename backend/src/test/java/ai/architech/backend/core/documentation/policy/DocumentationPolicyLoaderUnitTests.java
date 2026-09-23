package ai.architech.backend.core.documentation.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class DocumentationPolicyLoaderUnitTests {

	@Mock
	private ResourcePatternResolver resourceResolver;

	@Test
	void wrapsAScanFailureAsAnUncheckedIOException() throws IOException {
		when(resourceResolver.getResources(anyString())).thenThrow(new IOException("boom"));

		DocumentationPolicyLoader loader = new DocumentationPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void rejectsMoreThanOnePolicyResourceOnTheClasspath() throws IOException {
		Resource first = new ByteArrayResource(new byte[0], "documentation-policy.yaml");
		Resource second = new ByteArrayResource(new byte[0], "documentation-policy-duplicate.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {first, second});

		DocumentationPolicyLoader loader = new DocumentationPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class).hasMessageContaining("found 2");
	}

	@Test
	void wrapsAMalformedPolicyAsAnInvalidDocumentationPolicyException() throws IOException {
		Resource malformed = new ByteArrayResource("policyId: DOCUMENTATION_POLICY\n".getBytes(), "malformed-policy.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {malformed});

		DocumentationPolicyLoader loader = new DocumentationPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(InvalidDocumentationPolicyException.class);
	}

	@Test
	void wrapsAnUnreadableResourceAsAnInvalidDocumentationPolicyException() throws IOException {
		Resource unreadable = mock(Resource.class);
		when(unreadable.getInputStream()).thenThrow(new IOException("disk error"));
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {unreadable});

		DocumentationPolicyLoader loader = new DocumentationPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(InvalidDocumentationPolicyException.class);
	}
}
