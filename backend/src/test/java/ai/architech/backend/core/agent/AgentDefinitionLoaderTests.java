package ai.architech.backend.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentDefinitionLoaderTests {

	@Autowired
	private AgentDefinitionLoader loader;

	@Test
	void resolvesTheFrozenRequirementsAgentDefinition() {
		AgentDefinition definition = loader.resolve("requirements-agent", 1);

		assertThat(definition.id()).isEqualTo("requirements-agent");
		assertThat(definition.name()).isEqualTo("Website Requirements Analyst");
		assertThat(definition.modelProfile()).isEqualTo("structured-reasoning");
		assertThat(definition.limits().maxOutputTokens()).isEqualTo(8000);
		assertThat(definition.skills()).containsExactly("extract-business-requirements");
		assertThat(definition.rules()).containsExactly("requirements-integrity");
		assertThat(definition.outputs().artifacts()).hasSize(2);
		assertThat(definition.outputs().artifacts())
				.extracting(AgentArtifactOutput::type)
				.containsExactlyInAnyOrder("customer-profile", "website-requirements");
		assertThat(definition.outputs().artifacts()).allMatch(AgentArtifactOutput::required);
		assertThat(definition.roleContent()).contains("# Website Requirements Analyst");
		assertThat(definition.roleContent()).contains("The agent analyzes evidence.");

		// AIW-127: each declared output's schema file is resolved and loaded, not just referenced by path
		AgentArtifactOutput customerProfile = definition.outputs().artifacts().stream()
				.filter(output -> output.type().equals("customer-profile"))
				.findFirst()
				.orElseThrow();
		assertThat(customerProfile.schemaContent()).contains("\"title\": \"Customer Profile\"");
		AgentArtifactOutput websiteRequirements = definition.outputs().artifacts().stream()
				.filter(output -> output.type().equals("website-requirements"))
				.findFirst()
				.orElseThrow();
		assertThat(websiteRequirements.schemaContent()).contains("\"title\": \"Website Requirements\"");
	}

	@Test
	void throwsWhenNoDefinitionMatchesTheRequestedIdAndVersion() {
		assertThatThrownBy(() -> loader.resolve("requirements-agent", 99))
				.isInstanceOf(AgentDefinitionNotFoundException.class);

		assertThatThrownBy(() -> loader.resolve("does-not-exist", 1))
				.isInstanceOf(AgentDefinitionNotFoundException.class);
	}
}
