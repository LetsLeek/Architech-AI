package ai.architech.backend.projecttype.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.agentexecution.AgentExecution;
import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeveloperExecutionStatusResponseTests {

	@Test
	void aSucceededExecutionWithNoAcceptedCandidateIsARejectedInvariantViolation() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);
		execution.start();
		execution.succeed();

		assertThatThrownBy(() -> DeveloperExecutionStatusResponse.from(execution, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("has no accepted Candidate");
	}

	@Test
	void neverReportsACandidateForANonSucceededExecutionEvenIfOneWasSomehowPassedIn() {
		AgentExecution execution = new AgentExecution(UUID.randomUUID(), "developer-agent", 1);
		execution.start();
		execution.fail("output-contract validation failed");
		WebsiteImplementationCandidate stray = new WebsiteImplementationCandidate(
				UUID.randomUUID(), execution.getId(), "design-v1", "prop-a", "runtime-v1", "snapshot-hash-1", "summary", "[]", "[]", "[]");

		DeveloperExecutionStatusResponse response = DeveloperExecutionStatusResponse.from(execution, stray);

		assertThat(response.candidateId()).isNull();
		assertThat(response.repositoryStateRef()).isNull();
		assertThat(response.targetDesignArtifactVersionRef()).isNull();
		assertThat(response.targetDesignProposalLocalRef()).isNull();
		assertThat(response.failureReason()).isEqualTo("output-contract validation failed");
	}
}
