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
 * Verifies the frozen documentation-agent handoff package (AIW-187) is actually intact and
 * internally consistent - mirrors {@link FrozenWebsiteQaAgentSpecIT}. Like QA, the Documentation
 * Agent declares exactly one input artifact type ({@code documentation-context}) and exactly one
 * output artifact type ({@code semantic-documentation-candidate}), both opaque-ref-only payloads -
 * Documentation never re-validates the upstream Requirements/Design/Implementation/QA documents
 * themselves, it only references an already-frozen {@code DocumentationContext}.
 */
@SpringBootTest
class FrozenDocumentationAgentSpecIT {

	private static final String AGENT_DEFINITION_PATTERN = "classpath*:project-types/**/agent.yaml";

	@Autowired
	private ResourcePatternResolver resourceResolver;

	@Autowired
	private AgentDefinitionLoader agentDefinitionLoader;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void everyDeclaredInputAndOutputSchemaExistsAndIsValidJson() throws IOException {
		AgentDefinition definition = agentDefinitionLoader.resolve("website-documentation-agent", 1);
		assertThat(definition.inputs().artifacts()).hasSize(1);
		assertThat(definition.outputs().artifacts()).hasSize(1);

		Resource[] agentYamls = resourceResolver.getResources(AGENT_DEFINITION_PATTERN);
		assertThat(agentYamls).hasSize(5);
		Resource agentYaml = findAgentYamlById(agentYamls, "website-documentation-agent");

		for (AgentArtifactInput artifact : definition.inputs().artifacts()) {
			assertSchemaExistsAndIsValidJson(agentYaml, artifact.type(), artifact.schema());
		}
		for (AgentArtifactOutput artifact : definition.outputs().artifacts()) {
			assertSchemaExistsAndIsValidJson(agentYaml, artifact.type(), artifact.schema());
		}
	}

	@Test
	void bindsExactlyOneDocumentationContextInputAndOneSemanticDocumentationCandidateOutput() {
		AgentDefinition definition = agentDefinitionLoader.resolve("website-documentation-agent", 1);

		assertThat(definition.inputs().artifacts()).extracting(AgentArtifactInput::type).containsExactly(
				"documentation-context");
		assertThat(definition.outputs().artifacts()).extracting(AgentArtifactOutput::type).containsExactly(
				"semantic-documentation-candidate");
		assertThat(definition.modelProfile()).isEqualTo("documentation-reasoning");
		assertThat(definition.rules()).containsExactly("documentation-integrity");
	}

	@Test
	void loadsAllNineFrozenSkillsByTheirOwnIds() {
		AgentDefinition definition = agentDefinitionLoader.resolve("website-documentation-agent", 1);

		assertThat(definition.skills())
				.containsExactlyInAnyOrder(
						"authority-preserving-synthesis",
						"claim-construction-and-traceability",
						"audience-adaptation",
						"epistemic-state-representation",
						"finding-and-limitation-writing",
						"localization-and-terminology",
						"semantic-self-review",
						"customer-handover-composition",
						"technical-handover-composition");
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
