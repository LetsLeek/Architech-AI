package ai.architech.backend.projecttype.website;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.Artifact;
import ai.architech.backend.core.artifact.ArtifactRepository;
import ai.architech.backend.core.artifact.ArtifactVersion;
import ai.architech.backend.core.artifact.ArtifactVersionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import ai.architech.backend.core.security.DefaultApiKeyHeaderConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DefaultApiKeyHeaderConfig.class) // AIW-185: attaches a valid X-API-Key to every MockMvc request by default
@Transactional
class DesignerReadinessControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Test
	void rejectsAnUnknownProject() throws Exception {
		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void notReadyWhenNeitherCanonicalInputExists() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerProfileExists").value(false))
				.andExpect(jsonPath("$.websiteRequirementsExists").value(false))
				.andExpect(jsonPath("$.ready").value(false))
				.andExpect(jsonPath("$.running").value(false))
				.andExpect(jsonPath("$.latestExecutionId").value(nullValue()))
				.andExpect(jsonPath("$.latestExecutionStatus").value(nullValue()))
				.andExpect(jsonPath("$.designProposalSetExists").value(false));
	}

	@Test
	void notReadyWhenOnlyOneCanonicalInputExists() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		seedArtifact(project.getId(), "customer-profile", "{}");

		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerProfileExists").value(true))
				.andExpect(jsonPath("$.websiteRequirementsExists").value(false))
				.andExpect(jsonPath("$.ready").value(false));
	}

	@Test
	void readyWithNoPriorExecutionWhenBothCanonicalInputsExist() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		seedArtifact(project.getId(), "customer-profile", "{}");
		seedArtifact(project.getId(), "website-requirements", "{}");

		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerProfileExists").value(true))
				.andExpect(jsonPath("$.websiteRequirementsExists").value(true))
				.andExpect(jsonPath("$.ready").value(true))
				.andExpect(jsonPath("$.running").value(false))
				.andExpect(jsonPath("$.latestExecutionId").value(nullValue()))
				.andExpect(jsonPath("$.designProposalSetExists").value(false));
	}

	@Test
	void reportsARunningDesignerExecution() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		seedArtifact(project.getId(), "customer-profile", "{}");
		seedArtifact(project.getId(), "website-requirements", "{}");
		AgentExecution execution = new AgentExecution(project.getId(), "designer-agent", 1);
		execution.start();
		agentExecutionRepository.saveAndFlush(execution);

		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(true))
				.andExpect(jsonPath("$.latestExecutionId").value(execution.getId().toString()))
				.andExpect(jsonPath("$.latestExecutionStatus").value("RUNNING"))
				.andExpect(jsonPath("$.latestExecutionFailureReason").value(nullValue()));
	}

	@Test
	void reportsASucceededDesignerExecutionAndTheResultingDesignProposalSetReference() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		seedArtifact(project.getId(), "customer-profile", "{}");
		seedArtifact(project.getId(), "website-requirements", "{}");
		AgentExecution execution = new AgentExecution(project.getId(), "designer-agent", 1);
		execution.start();
		execution.succeed();
		agentExecutionRepository.saveAndFlush(execution);
		Artifact designProposalSet = seedArtifact(project.getId(), "design-proposal-set", "{\"proposals\":[]}");

		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(false))
				.andExpect(jsonPath("$.latestExecutionStatus").value("SUCCEEDED"))
				.andExpect(jsonPath("$.latestExecutionFailureReason").value(nullValue()))
				.andExpect(jsonPath("$.designProposalSetExists").value(true))
				.andExpect(jsonPath("$.designProposalSetArtifactId").value(designProposalSet.getId().toString()))
				.andExpect(jsonPath("$.designProposalSetVersionNumber").value(1));
	}

	@Test
	void reportsAFailedDesignerExecutionsFailureReasonWithoutADesignProposalSet() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		seedArtifact(project.getId(), "customer-profile", "{}");
		seedArtifact(project.getId(), "website-requirements", "{}");
		AgentExecution execution = new AgentExecution(project.getId(), "designer-agent", 1);
		execution.start();
		execution.fail("output-contract validation failed");
		agentExecutionRepository.saveAndFlush(execution);

		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(false))
				.andExpect(jsonPath("$.latestExecutionStatus").value("FAILED"))
				.andExpect(jsonPath("$.latestExecutionFailureReason").value("output-contract validation failed"))
				.andExpect(jsonPath("$.designProposalSetExists").value(false));
	}

	@Test
	void onlyConsidersExecutionsOfTheDesignerAgentNeverAnotherAgentForTheSameProject() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution otherAgentExecution = new AgentExecution(project.getId(), "requirements-agent", 1);
		otherAgentExecution.start();
		agentExecutionRepository.saveAndFlush(otherAgentExecution);

		mockMvc.perform(get("/api/projects/{projectId}/design-readiness", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(false))
				.andExpect(jsonPath("$.latestExecutionId").value(nullValue()))
				.andExpect(jsonPath("$.latestExecutionStatus").value(nullValue()));
	}

	private Artifact seedArtifact(UUID projectId, String type, String content) {
		Artifact artifact = artifactRepository.saveAndFlush(new Artifact(projectId, type));
		AgentExecution producingExecution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		artifactVersionRepository.saveAndFlush(new ArtifactVersion(artifact.getId(), 1, producingExecution.getId(), content));
		return artifact;
	}
}
