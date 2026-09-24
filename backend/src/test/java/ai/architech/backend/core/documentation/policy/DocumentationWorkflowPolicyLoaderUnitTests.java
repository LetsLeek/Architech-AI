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
class DocumentationWorkflowPolicyLoaderUnitTests {

	@Mock
	private ResourcePatternResolver resourceResolver;

	@Test
	void wrapsAScanFailureAsAnUncheckedIOException() throws IOException {
		when(resourceResolver.getResources(anyString())).thenThrow(new IOException("boom"));

		DocumentationWorkflowPolicyLoader loader = new DocumentationWorkflowPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void rejectsMoreThanOneWorkflowPolicyResourceOnTheClasspath() throws IOException {
		Resource first = new ByteArrayResource(new byte[0], "documentation-workflow-policy.yaml");
		Resource second = new ByteArrayResource(new byte[0], "documentation-workflow-policy-duplicate.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {first, second});

		DocumentationWorkflowPolicyLoader loader = new DocumentationWorkflowPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class).hasMessageContaining("found 2");
	}

	@Test
	void wrapsAMalformedWorkflowPolicyAsAnInvalidDocumentationPolicyException() throws IOException {
		Resource malformed =
				new ByteArrayResource("workflowPolicyId: DOCUMENTATION_WORKFLOW_POLICY\n".getBytes(), "malformed-workflow-policy.yaml");
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {malformed});

		DocumentationWorkflowPolicyLoader loader = new DocumentationWorkflowPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(InvalidDocumentationPolicyException.class);
	}

	@Test
	void wrapsAnUnreadableResourceAsAnInvalidDocumentationPolicyException() throws IOException {
		Resource unreadable = mock(Resource.class);
		when(unreadable.getInputStream()).thenThrow(new IOException("disk error"));
		when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {unreadable});

		DocumentationWorkflowPolicyLoader loader = new DocumentationWorkflowPolicyLoader(resourceResolver);

		assertThatThrownBy(loader::load).isInstanceOf(InvalidDocumentationPolicyException.class);
	}
}
