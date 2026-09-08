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
class CandidatePromoterTests {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private CandidateOutputRepository candidateOutputRepository;

	@Autowired
	private CandidatePromoter candidatePromoter;

	@Autowired
	private ArtifactRepository artifactRepository;

	@Test
	void recordingACandidateNeverCreatesAnArtifactVersionOnItsOwn() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));

		candidateOutputRepository.saveAndFlush(
				new CandidateOutput(execution.getId(), "customer-profile", "{\"raw\":true}"));

		// scoped to this test's own project - a table-wide findAll().isEmpty() would depend on
		// no unrelated Artifact existing anywhere in the (shared, dev-usable) database
		assertThat(artifactRepository.findByProjectIdAndType(project.getId(), "customer-profile")).isEmpty();
	}

	@Test
	void promotingARecordedCandidateCreatesTheCanonicalArtifactVersion() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));
		CandidateOutput candidate = candidateOutputRepository.saveAndFlush(
				new CandidateOutput(execution.getId(), "customer-profile", "{\"businessName\":\"Foo Bakery\"}"));

		ArtifactVersion version = candidatePromoter.promote(project.getId(), candidate);

		assertThat(version.getVersionNumber()).isEqualTo(1);
		assertThat(version.getContent()).isEqualTo("{\"businessName\":\"Foo Bakery\"}");
		assertThat(version.getAgentExecutionId()).isEqualTo(execution.getId());

		Artifact artifact = artifactRepository.findById(version.getArtifactId()).orElseThrow();
		assertThat(artifact.getType()).isEqualTo("customer-profile");

		// the candidate itself is untouched - promotion never mutates or deletes it
		CandidateOutput reloaded =
				candidateOutputRepository.findById(candidate.getId()).orElseThrow();
		assertThat(reloaded.getContent()).isEqualTo("{\"businessName\":\"Foo Bakery\"}");
	}

	@Test
	void anInvalidCandidateThatIsNeverPromotedLeavesNoArtifactVersionBehind() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		AgentExecution execution = agentExecutionRepository.saveAndFlush(
				new AgentExecution(project.getId(), "requirements-agent", 1));

		// simulates a candidate that failed validation - a caller simply never calls promote()
		candidateOutputRepository.saveAndFlush(
				new CandidateOutput(execution.getId(), "customer-profile", "not valid json at all"));

		assertThat(artifactRepository.findByProjectIdAndType(project.getId(), "customer-profile")).isEmpty();
	}
}
