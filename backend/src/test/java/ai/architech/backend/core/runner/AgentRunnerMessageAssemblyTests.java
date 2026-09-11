package ai.architech.backend.core.runner;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.agent.AgentArtifactOutput;
import ai.architech.backend.core.agent.AgentDefinition;
import ai.architech.backend.core.agent.AgentLimits;
import ai.architech.backend.core.agent.AgentOutputs;
import ai.architech.backend.core.ai.AiMessage;
import ai.architech.backend.core.evidence.ReferencedSourceContext;
import ai.architech.backend.core.evidence.ReferencedSourceItem;
import ai.architech.backend.core.evidence.SourceOrigin;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test for {@link AgentRunner#buildMessages} - no Spring context, no AI Gateway
 * call. Proves AIW-127: every required output's exact schema text ends up in the system
 * message, not just described in prose.
 */
class AgentRunnerMessageAssemblyTests {

	@Test
	void includesEveryRequiredOutputsExactSchemaTextInTheSystemMessage() {
		String customerProfileSchema = "{\"type\":\"object\",\"title\":\"Customer Profile Schema Marker\"}";
		String websiteRequirementsSchema = "{\"type\":\"object\",\"title\":\"Website Requirements Schema Marker\"}";

		AgentDefinition agentDefinition = new AgentDefinition(
				1,
				"requirements-agent",
				"Website Requirements Analyst",
				1,
				"desc",
				"structured-reasoning",
				new AgentLimits(1000),
				List.of(),
				List.of(),
				new AgentOutputs(List.of(
						new AgentArtifactOutput(
								"customer-profile", "../../schemas/customer-profile.schema.json", customerProfileSchema, true),
						new AgentArtifactOutput(
								"website-requirements",
								"../../schemas/website-requirements.schema.json",
								websiteRequirementsSchema,
								true))),
				"You are the Requirements Agent.");

		ReferencedSourceContext sourceContext = new ReferencedSourceContext(
				UUID.randomUUID(), List.of(new ReferencedSourceItem("SRC-1", SourceOrigin.FREE_TEXT, "We are a bakery.")));

		List<AiMessage> messages = AgentRunner.buildMessages(agentDefinition, List.of(), List.of(), sourceContext);

		AiMessage systemMessage =
				messages.stream().filter(message -> "system".equals(message.role())).findFirst().orElseThrow();

		assertThat(systemMessage.content()).contains(customerProfileSchema);
		assertThat(systemMessage.content()).contains(websiteRequirementsSchema);
		assertThat(systemMessage.content()).contains("Schema for \"customer-profile\"");
		assertThat(systemMessage.content()).contains("Schema for \"website-requirements\"");
		assertThat(systemMessage.content())
				.contains("Its only top-level keys must be exactly: \"customer-profile\", \"website-requirements\"");
	}
}
