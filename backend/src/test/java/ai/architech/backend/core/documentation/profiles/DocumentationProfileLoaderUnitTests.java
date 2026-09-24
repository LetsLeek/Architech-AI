package ai.architech.backend.core.documentation.profiles;

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
class DocumentationProfileLoaderUnitTests {

	@Mock
	private ResourcePatternResolver resourceResolver;

	@Test
	void wrapsAScanFailureAsAnUncheckedIOException() throws IOException {
		when(resourceResolver.getResources(anyString())).thenThrow(new IOException("boom"));

		DocumentationProfileLoader loader = new DocumentationProfileLoader(resourceResolver);

		assertThatThrownBy(() -> loader.resolve("CUSTOMER_HANDOVER@1.0.0")).isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void wrapsAMalformedProfileAsAnInvalidDocumentationProfileException() throws IOException {
		Resource malformed = new ByteArrayResource("active: true\n".getBytes(), "malformed-profile.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {malformed});

		DocumentationProfileLoader loader = new DocumentationProfileLoader(resourceResolver);

		assertThatThrownBy(() -> loader.resolve("CUSTOMER_HANDOVER@1.0.0")).isInstanceOf(InvalidDocumentationProfileException.class);
	}

	@Test
	void wrapsAnUnreadableResourceAsAnInvalidDocumentationProfileException() throws IOException {
		Resource unreadable = mock(Resource.class);
		when(unreadable.getInputStream()).thenThrow(new IOException("disk error"));
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {unreadable});

		DocumentationProfileLoader loader = new DocumentationProfileLoader(resourceResolver);

		assertThatThrownBy(() -> loader.resolve("CUSTOMER_HANDOVER@1.0.0")).isInstanceOf(InvalidDocumentationProfileException.class);
	}
}
