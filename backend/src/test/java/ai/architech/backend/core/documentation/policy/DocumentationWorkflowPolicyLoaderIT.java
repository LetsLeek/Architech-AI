package ai.architech.backend.core.documentation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DocumentationWorkflowPolicyLoaderIT {

	@Autowired
	private DocumentationWorkflowPolicyLoader loader;

	@Test
	void loadsTheFrozenWorkflowPolicyWithItsExpectedRefAndBoundedRetryLimits() {
		DocumentationWorkflowPolicy policy = loader.load();

		assertThat(policy.ref()).isEqualTo("DOCUMENTATION_WORKFLOW_POLICY@1.0.0");
		assertThat(policy.maxGenerationAttempts()).isEqualTo(3);
		assertThat(policy.maxSemanticEvaluatorExecutionsPerUnchangedCandidate()).isEqualTo(2);
		assertThat(policy.triggerIdempotencyRequired()).isTrue();
		assertThat(policy.productGateDependency()).isFalse();
	}

	@Test
	void enabledTriggersCoverTechnicalOnFullReleaseAndCustomerOnScopedApproval() {
		DocumentationWorkflowPolicy policy = loader.load();

		assertThat(policy.enabledTriggers()).hasSize(2);
		assertThat(policy.enabledTriggers()).extracting(WorkflowTrigger::profileRef)
				.containsExactlyInAnyOrder("TECHNICAL_HANDOVER@1.0.0", "CUSTOMER_HANDOVER@1.0.0");
		assertThat(policy.enabledTriggers()).allMatch(t -> t.event().isPresent());
	}

	@Test
	void onDemandTriggersCoverBothProfilesWithoutAnEventField() {
		DocumentationWorkflowPolicy policy = loader.load();

		assertThat(policy.onDemand()).hasSize(2);
		assertThat(policy.onDemand()).allMatch(t -> t.event().isEmpty());
	}

	@Test
	void optionalRefreshAndIgnoredTriggersAreDeclared() {
		DocumentationWorkflowPolicy policy = loader.load();

		assertThat(policy.optionalRefreshEvents()).containsExactly("DEPLOYMENT_RECORDED");
		assertThat(policy.ignoredAutomaticTriggers()).hasSize(4);
	}
}
