package ai.architech.backend.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentDefinitionLoaderIT {

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
	void resolvesTheFrozenDesignerAgentDefinition() {
		AgentDefinition definition = loader.resolve("designer-agent", 1);

		assertThat(definition.id()).isEqualTo("designer-agent");
		assertThat(definition.name()).isEqualTo("Website Designer");
		assertThat(definition.modelProfile()).isEqualTo("design-reasoning");
		assertThat(definition.limits().maxOutputTokens()).isEqualTo(16000);
		assertThat(definition.skills()).containsExactly("plan-website-design");
		assertThat(definition.rules()).containsExactly("design-integrity");
		assertThat(definition.roleContent()).contains("# Website Designer");
		assertThat(definition.roleContent()).contains("The agent makes independent design decisions.");

		// AIW-116: unlike requirements-agent, this agent declares required prior-artifact inputs.
		assertThat(definition.inputs().artifacts()).hasSize(2);
		assertThat(definition.inputs().artifacts())
				.extracting(AgentArtifactInput::type)
				.containsExactlyInAnyOrder("customer-profile", "website-requirements");
		assertThat(definition.inputs().artifacts()).allMatch(AgentArtifactInput::required);

		assertThat(definition.outputs().artifacts()).hasSize(1);
		assertThat(definition.outputs().artifacts().get(0).type()).isEqualTo("design-proposal-set");
		assertThat(definition.outputs().artifacts().get(0).required()).isTrue();
		assertThat(definition.outputs().artifacts().get(0).schemaContent()).contains("\"title\": \"Website Design Proposal Set\"");
	}

	@Test
	void agentsWithNoDeclaredInputsHaveAnEmptyInputsArtifactsList() {
		AgentDefinition definition = loader.resolve("requirements-agent", 1);

		assertThat(definition.inputs().artifacts()).isEmpty();
	}

	@Test
	void throwsWhenNoDefinitionMatchesTheRequestedIdAndVersion() {
		assertThatThrownBy(() -> loader.resolve("requirements-agent", 99))
				.isInstanceOf(AgentDefinitionNotFoundException.class);

		assertThatThrownBy(() -> loader.resolve("does-not-exist", 1))
				.isInstanceOf(AgentDefinitionNotFoundException.class);
	}
}
