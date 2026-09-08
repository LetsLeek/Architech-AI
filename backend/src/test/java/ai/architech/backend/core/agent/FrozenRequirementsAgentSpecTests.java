package ai.architech.backend.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the frozen requirements-agent handoff package (AIW-46) is actually intact and
 * internally consistent, on top of what AIW-30/31/32's loader tests already prove (agent,
 * rule, and all 13 skill modules load correctly): every schema the agent definition
 * declares under {@code outputs.artifacts} genuinely exists next to it and is valid JSON.
 * The loaders/rule/skill content are already covered elsewhere - this test only closes the
 * one remaining gap (schema presence) rather than re-proving what's already tested.
 *
 * <p>No new production code exists for this ticket: docs/core/ and project-types/ have
 * been at their intended repo-root location, untouched, since AIW-15, and every loader
 * built since (AIW-30..32) already reads directly from there. There was nothing left to
 * "integrate" beyond confirming it's genuinely all present and self-consistent.
 */
@SpringBootTest
class FrozenRequirementsAgentSpecTests {

	private static final String AGENT_DEFINITION_PATTERN = "classpath*:project-types/**/agent.yaml";

	@Autowired
	private ResourcePatternResolver resourceResolver;

	@Autowired
	private AgentDefinitionLoader agentDefinitionLoader;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void everySchemaTheFrozenAgentDefinitionDeclaresExistsAndIsValidJson() throws IOException {
		AgentDefinition definition = agentDefinitionLoader.resolve("requirements-agent", 1);
		assertThat(definition.outputs().artifacts()).hasSize(2);

		// Only one agent.yaml exists in the repo today, so it's unambiguously "the" resource
		// backing the definition just resolved above - revisit if a second agent is ever added.
		Resource[] agentYamls = resourceResolver.getResources(AGENT_DEFINITION_PATTERN);
		assertThat(agentYamls).hasSize(1);
		Resource agentYaml = agentYamls[0];

		for (AgentArtifactOutput artifact : definition.outputs().artifacts()) {
			Resource schema = agentYaml.createRelative(artifact.schema());
			assertThat(schema.exists()).as("schema for artifact type '%s'", artifact.type()).isTrue();

			try (InputStream in = schema.getInputStream()) {
				var tree = objectMapper.readTree(in);
				assertThat(tree.get("$schema"))
						.as("schema for artifact type '%s' is valid JSON Schema", artifact.type())
						.isNotNull();
			}
		}
	}
}
