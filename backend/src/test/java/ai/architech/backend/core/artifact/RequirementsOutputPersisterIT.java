package ai.architech.backend.core.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.agentexecution.AgentExecutionRepository;
import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RequirementsOutputPersisterIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private CandidateOutputRepository candidateOutputRepository;

	@Autowired
	private RequirementsOutputPersister requirementsOutputPersister;

	@Autowired
	private ArtifactVersionRepository artifactVersionRepository;

	@Test
	void bothCandidatesValidPersistsBothAsCanonicalVersionOne() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));
		CandidateOutput profile = candidateOutputRepository.saveAndFlush(new CandidateOutput(
				execution.getId(), RequirementsOutputPersister.CUSTOMER_PROFILE_TYPE, "{\"business\":\"Foo\"}"));
		CandidateOutput requirements = candidateOutputRepository.saveAndFlush(new CandidateOutput(
				execution.getId(), RequirementsOutputPersister.WEBSITE_REQUIREMENTS_TYPE, "{\"goals\":[]}"));

		RequirementsPersistenceResult result =
				requirementsOutputPersister.persist(project.getId(), profile, true, requirements, true);

		assertThat(result.persisted()).isTrue();
		assertThat(result.customerProfileVersion().getVersionNumber()).isEqualTo(1);
		assertThat(result.websiteRequirementsVersion().getVersionNumber()).isEqualTo(1);
		assertThat(result.customerProfileVersion().getArtifactId())
				.isNotEqualTo(result.websiteRequirementsVersion().getArtifactId());
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).hasSize(2);
	}

	@Test
	void eitherCandidateInvalidPersistsNeither() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));
		CandidateOutput profile = candidateOutputRepository.saveAndFlush(new CandidateOutput(
				execution.getId(), RequirementsOutputPersister.CUSTOMER_PROFILE_TYPE, "{\"business\":\"Foo\"}"));
		CandidateOutput requirements = candidateOutputRepository.saveAndFlush(new CandidateOutput(
				execution.getId(), RequirementsOutputPersister.WEBSITE_REQUIREMENTS_TYPE, "not valid"));

		// profile passed validation, requirements did not - neither should become canonical
		RequirementsPersistenceResult result =
				requirementsOutputPersister.persist(project.getId(), profile, true, requirements, false);

		assertThat(result.persisted()).isFalse();
		assertThat(result.customerProfileVersion()).isNull();
		assertThat(result.websiteRequirementsVersion()).isNull();
		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}

	@Test
	void rejectsAMismatchedCandidateTypeBeforeWritingAnything() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));
		CandidateOutput wrongType = candidateOutputRepository.saveAndFlush(
				new CandidateOutput(execution.getId(), "not-the-right-type", "{}"));
		CandidateOutput requirements = candidateOutputRepository.saveAndFlush(new CandidateOutput(
				execution.getId(), RequirementsOutputPersister.WEBSITE_REQUIREMENTS_TYPE, "{}"));

		assertThatThrownBy(
						() -> requirementsOutputPersister.persist(project.getId(), wrongType, true, requirements, true))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(artifactVersionRepository.findByAgentExecutionId(execution.getId())).isEmpty();
	}
}
