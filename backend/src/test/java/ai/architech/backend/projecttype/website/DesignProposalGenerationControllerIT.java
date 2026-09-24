package ai.architech.backend.projecttype.website;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.artifact.CandidateOutput;
import ai.architech.backend.core.artifact.CandidateOutputRepository;
import ai.architech.backend.core.artifact.CandidatePromoter;
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
class DesignProposalGenerationControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private CandidateOutputRepository candidateOutputRepository;

	@Autowired
	private CandidatePromoter candidatePromoter;

	@Test
	void rejectsStartingForAnUnknownProject() throws Exception {
		mockMvc.perform(post("/api/projects/{id}/design-proposals", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void rejectsStartingWhenNoCanonicalRequirementsArtifactsExistYet() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		mockMvc.perform(post("/api/projects/{id}/design-proposals", project.getId())).andExpect(status().isNotFound());
	}

	@Test
	void startsGenerationAndReturnsATerminalStatus() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		promoteCanonicalArtifact(project.getId(), "customer-profile", """
				{"locations": [{"localRef": "cust-1"}]}
				""");
		promoteCanonicalArtifact(project.getId(), "website-requirements", """
				{"goals": [{"localRef": "req-1"}]}
				""");

		// The only registered AI provider is the deterministic mock, which always returns empty
		// content - output-contract validation always fails, so this always ends up FAILED.
		// That's the point: prove the endpoint drives the full pipeline end-to-end, not that it
		// succeeds (same reasoning as RequirementsAnalysisControllerIT's own equivalent test).
		mockMvc.perform(post("/api/projects/{id}/design-proposals", project.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.executionId").exists())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.succeeded").value(false))
				.andExpect(jsonPath("$.validationIssues").isNotEmpty())
				.andExpect(jsonPath("$.provider").value("mock"))
				.andExpect(jsonPath("$.model").value("mock-model"))
				.andExpect(jsonPath("$.promptTokens").value(nullValue()))
				.andExpect(jsonPath("$.completionTokens").value(nullValue()))
				.andExpect(jsonPath("$.costUsd").value(nullValue()));
	}

	@Test
	void rejectsStartingWhenAGenerationIsAlreadyRunningForTheProject() throws Exception {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		promoteCanonicalArtifact(project.getId(), "customer-profile", """
				{"locations": []}
				""");
		promoteCanonicalArtifact(project.getId(), "website-requirements", """
				{"goals": []}
				""");
		AgentExecution running = new AgentExecution(project.getId(), "designer-agent", 1);
		running.start();
		agentExecutionRepository.saveAndFlush(running);

		mockMvc.perform(post("/api/projects/{id}/design-proposals", project.getId())).andExpect(status().isConflict());
	}

	private void promoteCanonicalArtifact(UUID projectId, String type, String content) {
		AgentExecution seedExecution = agentExecutionRepository.saveAndFlush(new AgentExecution(projectId, "requirements-agent", 1));
		CandidateOutput candidate = candidateOutputRepository.saveAndFlush(new CandidateOutput(seedExecution.getId(), type, content));
		candidatePromoter.promote(projectId, candidate);
	}
}
