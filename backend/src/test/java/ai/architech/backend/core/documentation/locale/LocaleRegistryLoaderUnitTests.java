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
class LocaleRegistryLoaderUnitTests {

	@Mock
	private ResourcePatternResolver resourceResolver;

	@Test
	void wrapsAScanFailureAsAnUncheckedIOException() throws IOException {
		when(resourceResolver.getResources(anyString())).thenThrow(new IOException("boom"));

		LocaleRegistryLoader loader = new LocaleRegistryLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void rejectsMoreThanOneLocaleRegistryResourceOnTheClasspath() throws IOException {
		Resource first = new ByteArrayResource(new byte[0], "locale-registry.yaml");
		Resource second = new ByteArrayResource(new byte[0], "locale-registry-duplicate.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {first, second});

		LocaleRegistryLoader loader = new LocaleRegistryLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class).hasMessageContaining("found 2");
	}

	@Test
	void wrapsAMalformedLocaleRegistryAsAnInvalidLocaleRegistryException() throws IOException {
		Resource malformed = new ByteArrayResource("registryVersion: 1.0.0\n".getBytes(), "malformed-locale-registry.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {malformed});

		LocaleRegistryLoader loader = new LocaleRegistryLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(InvalidLocaleRegistryException.class);
	}

	@Test
	void wrapsAnUnreadableResourceAsAnInvalidLocaleRegistryException() throws IOException {
		Resource unreadable = mock(Resource.class);
		when(unreadable.getInputStream()).thenThrow(new IOException("disk error"));
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {unreadable});

		LocaleRegistryLoader loader = new LocaleRegistryLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(InvalidLocaleRegistryException.class);
	}
}
