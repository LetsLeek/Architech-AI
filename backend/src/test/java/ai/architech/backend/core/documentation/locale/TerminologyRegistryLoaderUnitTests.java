package ai.architech.backend.core.documentation.locale;

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
class TerminologyRegistryLoaderUnitTests {

	@Mock
	private ResourcePatternResolver resourceResolver;

	@Test
	void wrapsAScanFailureAsAnUncheckedIOException() throws IOException {
		when(resourceResolver.getResources(anyString())).thenThrow(new IOException("boom"));

		TerminologyRegistryLoader loader = new TerminologyRegistryLoader(resourceResolver);

		assertThatThrownBy(() -> loader.resolve("de-AT")).isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void wrapsAMalformedTerminologyRegistryAsAnInvalidLocaleRegistryException() throws IOException {
		Resource malformed = new ByteArrayResource("locale: de-AT\n".getBytes(), "malformed-terminology.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {malformed});

		TerminologyRegistryLoader loader = new TerminologyRegistryLoader(resourceResolver);

		assertThatThrownBy(() -> loader.resolve("de-AT")).isInstanceOf(InvalidLocaleRegistryException.class);
	}

	@Test
	void wrapsAnUnreadableResourceAsAnInvalidLocaleRegistryException() throws IOException {
		Resource unreadable = mock(Resource.class);
		when(unreadable.getInputStream()).thenThrow(new IOException("disk error"));
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {unreadable});

		TerminologyRegistryLoader loader = new TerminologyRegistryLoader(resourceResolver);

		assertThatThrownBy(() -> loader.resolve("de-AT")).isInstanceOf(InvalidLocaleRegistryException.class);
	}
}
