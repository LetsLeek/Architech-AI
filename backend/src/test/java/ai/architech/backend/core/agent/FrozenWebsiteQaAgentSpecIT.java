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
 * Verifies the frozen website-qa-agent handoff package (AIW-167) is actually intact and
 * internally consistent - mirrors {@link FrozenDeveloperAgentSpecIT}. Like Developer, the QA
 * Agent declares exactly one input artifact type ({@code qa-execution-input}) and exactly one
 * output artifact type ({@code semantic-qa-review-output}), both opaque-ref-only payloads - QA
 * never re-validates the upstream Customer Profile/Website Requirements/Design Proposal
 * documents, it only references an already-verified {@code WebsiteImplementationCandidate}.
 */
@SpringBootTest
class FrozenWebsiteQaAgentSpecIT {

	private static final String AGENT_DEFINITION_PATTERN = "classpath*:project-types/**/agent.yaml";

	@Autowired
	private ResourcePatternResolver resourceResolver;

	@Autowired
	private AgentDefinitionLoader agentDefinitionLoader;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void everyDeclaredInputAndOutputSchemaExistsAndIsValidJson() throws IOException {
		AgentDefinition definition = agentDefinitionLoader.resolve("website-qa-agent", 1);
		assertThat(definition.inputs().artifacts()).hasSize(1);
		assertThat(definition.outputs().artifacts()).hasSize(1);

		Resource[] agentYamls = resourceResolver.getResources(AGENT_DEFINITION_PATTERN);
		assertThat(agentYamls).hasSize(4);
		Resource agentYaml = findAgentYamlById(agentYamls, "website-qa-agent");

		for (AgentArtifactInput artifact : definition.inputs().artifacts()) {
			assertSchemaExistsAndIsValidJson(agentYaml, artifact.type(), artifact.schema());
		}
		for (AgentArtifactOutput artifact : definition.outputs().artifacts()) {
			assertSchemaExistsAndIsValidJson(agentYaml, artifact.type(), artifact.schema());
		}
	}

	@Test
	void bindsExactlyOneQaExecutionInputAndOneSemanticQaReviewOutput() {
		AgentDefinition definition = agentDefinitionLoader.resolve("website-qa-agent", 1);

		assertThat(definition.inputs().artifacts()).extracting(AgentArtifactInput::type).containsExactly(
				"qa-execution-input");
		assertThat(definition.outputs().artifacts()).extracting(AgentArtifactOutput::type).containsExactly(
				"semantic-qa-review-output");
		assertThat(definition.modelProfile()).isEqualTo("qa-reasoning");
		assertThat(definition.rules()).containsExactly("website-qa-integrity");
	}

	@Test
	void loadsAllSixteenFrozenSkillsByTheirOwnIds() {
		AgentDefinition definition = agentDefinitionLoader.resolve("website-qa-agent", 1);

		assertThat(definition.skills())
				.containsExactlyInAnyOrder(
						"website-qa-review",
						"evidence-assessment",
						"finding-construction",
						"requirement-fulfillment-review",
						"customer-fact-review",
						"design-fidelity-review",
						"content-quality-review",
						"functional-behavior-review",
						"navigation-flow-review",
						"responsive-quality-review",
						"visual-defect-review",
						"accessibility-semantic-review",
						"integration-behavior-review",
						"localization-review",
						"finding-deduplication",
						"remediation-reassessment");
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
