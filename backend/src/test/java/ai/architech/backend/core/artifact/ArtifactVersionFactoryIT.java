package ai.architech.backend.core.artifact;

import static org.assertj.core.api.Assertions.assertThat;

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
class ArtifactVersionFactoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private ArtifactVersionFactory artifactVersionFactory;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Test
	void firstVersionForATypeCreatesTheArtifactAndStartsAtOne() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));

		ArtifactVersion version =
				artifactVersionFactory.createNextVersion(project.getId(), "customer-profile", execution.getId(), "{}");

		assertThat(version.getVersionNumber()).isEqualTo(1);
		assertThat(version.getContent()).isEqualTo("{}");
		assertThat(version.getAgentExecutionId()).isEqualTo(execution.getId());

		Artifact artifact = artifactRepository.findById(version.getArtifactId()).orElseThrow();
		assertThat(artifact.getProjectId()).isEqualTo(project.getId());
		assertThat(artifact.getType()).isEqualTo("customer-profile");
	}

	@Test
	void secondVersionForTheSameTypeReusesTheArtifactAndIncrementsTheNumber() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution firstExecution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));
		AgentExecution secondExecution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));

		ArtifactVersion v1 = artifactVersionFactory.createNextVersion(
				project.getId(), "customer-profile", firstExecution.getId(), "{\"v\":1}");
		ArtifactVersion v2 = artifactVersionFactory.createNextVersion(
				project.getId(), "customer-profile", secondExecution.getId(), "{\"v\":2}");

		assertThat(v2.getArtifactId()).isEqualTo(v1.getArtifactId());
		assertThat(v2.getVersionNumber()).isEqualTo(2);
		// scoped to this project+type - a table-wide findAll().hasSize(1) would depend on no
		// unrelated Artifact existing anywhere in the (shared, dev-usable) database
		Artifact artifact = artifactRepository.findByProjectIdAndType(project.getId(), "customer-profile").orElseThrow();
		assertThat(artifact.getId()).isEqualTo(v1.getArtifactId());
	}

	@Test
	void differentArtifactTypesForTheSameProjectGetSeparateArtifacts() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));

		ArtifactVersion profile = artifactVersionFactory.createNextVersion(
				project.getId(), "customer-profile", execution.getId(), "{}");
		ArtifactVersion requirements = artifactVersionFactory.createNextVersion(
				project.getId(), "website-requirements", execution.getId(), "{}");

		assertThat(profile.getArtifactId()).isNotEqualTo(requirements.getArtifactId());
		assertThat(profile.getVersionNumber()).isEqualTo(1);
		assertThat(requirements.getVersionNumber()).isEqualTo(1);
	}
}
