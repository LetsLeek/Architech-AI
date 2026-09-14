package ai.architech.backend.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the frozen developer-agent handoff package (AIW-132..137) is actually intact and
 * internally consistent - mirrors {@link FrozenDesignerAgentSpecIT}. Unlike Designer, the
 * Developer Agent declares exactly one input artifact type ({@code developer-execution-input}):
 * its schema itself composes the canonical Customer Profile, Website Requirements and target
 * Design Proposal by {@code $ref} (see {@link ai.architech.backend.core.validation.DeveloperSchemaRegistry}),
 * rather than the Agent Contract declaring each of those three as its own separate input artifact.
 */
@SpringBootTest
class FrozenDeveloperAgentSpecIT {

	private static final String AGENT_DEFINITION_PATTERN = "classpath*:project-types/**/agent.yaml";

	@Autowired
	private ResourcePatternResolver resourceResolver;

	@Autowired
	private AgentDefinitionLoader agentDefinitionLoader;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void everyDeclaredInputAndOutputSchemaExistsAndIsValidJson() throws IOException {
		AgentDefinition definition = agentDefinitionLoader.resolve("developer-agent", 1);
		assertThat(definition.inputs().artifacts()).hasSize(1);
		assertThat(definition.outputs().artifacts()).hasSize(1);

		Resource[] agentYamls = resourceResolver.getResources(AGENT_DEFINITION_PATTERN);
		assertThat(agentYamls).hasSize(4);
		Resource agentYaml = findAgentYamlById(agentYamls, "developer-agent");

		for (AgentArtifactInput artifact : definition.inputs().artifacts()) {
			assertSchemaExistsAndIsValidJson(agentYaml, artifact.type(), artifact.schema());
		}
		for (AgentArtifactOutput artifact : definition.outputs().artifacts()) {
			assertSchemaExistsAndIsValidJson(agentYaml, artifact.type(), artifact.schema());
		}
	}

	@Test
	void bindsExactlyOneDeveloperExecutionInputAndOneDeveloperAgentResult() {
		AgentDefinition definition = agentDefinitionLoader.resolve("developer-agent", 1);

		assertThat(definition.inputs().artifacts()).extracting(AgentArtifactInput::type).containsExactly(
				"developer-execution-input");
		assertThat(definition.outputs().artifacts()).extracting(AgentArtifactOutput::type).containsExactly(
				"developer-agent-result");
		assertThat(definition.modelProfile()).isEqualTo("implementation-reasoning");
		assertThat(definition.skills()).containsExactly("website-developer");
		assertThat(definition.rules()).containsExactly("website-developer-integrity");
	}

	private void assertSchemaExistsAndIsValidJson(Resource agentYaml, String artifactType, String schemaPath)
			throws IOException {
		Resource schema = agentYaml.createRelative(schemaPath);
		assertThat(schema.exists()).as("schema for artifact type '%s'", artifactType).isTrue();

		try (InputStream in = schema.getInputStream()) {
			var tree = objectMapper.readTree(in);
			assertThat(tree.get("$schema")).as("schema for artifact type '%s' is valid JSON Schema", artifactType).isNotNull();
		}
	}

	private static Resource findAgentYamlById(Resource[] agentYamls, String id) throws IOException {
		Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
		for (Resource candidate : agentYamls) {
			Map<String, Object> raw;
			try (InputStream in = candidate.getInputStream()) {
				raw = yaml.load(in);
			}
			if (id.equals(raw.get("id"))) {
				return candidate;
			}
		}
		throw new IllegalStateException("No agent.yaml found with id '" + id + "'");
	}
}
