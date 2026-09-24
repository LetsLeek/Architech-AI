package ai.architech.backend.core.documentation.triggers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.architech.backend.projecttype.website.FullReleaseEligibility;
import ai.architech.backend.projecttype.website.FullReleaseEligibilityValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plain unit tests (no Spring context needed - {@link FullReleaseEligibilityValidator} is mocked
 * directly, its own constructor being package-private to {@code projecttype.website}) proving
 * {@link DocumentationWorkflowTriggerEvaluator}'s own per-event resolution rules, independent of
 * the full generation pipeline.
 */
class DocumentationWorkflowTriggerEvaluatorTests {

	private static final String FULL_RELEASE_QA_PROFILE_REF = "website-qa-full-release@1.0.0";

	private final FullReleaseEligibilityValidator fullReleaseEligibilityValidator = mock(FullReleaseEligibilityValidator.class);
	private final DocumentationWorkflowTriggerEvaluator evaluator =
			new DocumentationWorkflowTriggerEvaluator(fullReleaseEligibilityValidator);

	@Test
	void fullReleaseQaFinalizedResolvesToTechnicalHandoverWhenEligible() {
		UUID candidateId = UUID.randomUUID();
		stubEligibility(candidateId, true);

		Optional<String> resolved = evaluator.resolveProfileRef(DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, candidateId, false);

		assertThat(resolved).contains("TECHNICAL_HANDOVER@1.0.0");
	}

	@Test
	void fullReleaseQaFinalizedDoesNotResolveWhenNotEligible() {
		UUID candidateId = UUID.randomUUID();
		stubEligibility(candidateId, false);

		Optional<String> resolved = evaluator.resolveProfileRef(DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED, candidateId, false);

		assertThat(resolved).isEmpty();
	}

	@Test
	void scopedApprovalRecordedRequiresBothExternalConfirmationAndFullReleasePass() {
		UUID candidateId = UUID.randomUUID();
		stubEligibility(candidateId, true);

		assertThat(evaluator.resolveProfileRef(DocumentationTriggerEvent.SCOPED_APPROVAL_RECORDED, candidateId, false)).isEmpty();
		assertThat(evaluator.resolveProfileRef(DocumentationTriggerEvent.SCOPED_APPROVAL_RECORDED, candidateId, true))
				.contains("CUSTOMER_HANDOVER@1.0.0");
	}

	@Test
	void scopedApprovalRecordedNeverResolvesWithoutFullReleasePassEvenIfExternallyConfirmed() {
		UUID candidateId = UUID.randomUUID();
		stubEligibility(candidateId, false);

		Optional<String> resolved = evaluator.resolveProfileRef(DocumentationTriggerEvent.SCOPED_APPROVAL_RECORDED, candidateId, true);

		assertThat(resolved).isEmpty();
	}

	@Test
	void resolveProfileRefRejectsDeploymentRecordedSinceItIsNotAProfileSelectingEvent() {
		UUID candidateId = UUID.randomUUID();

		assertThatThrownBy(() -> evaluator.resolveProfileRef(DocumentationTriggerEvent.DEPLOYMENT_RECORDED, candidateId, false))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refreshGenerationReasonIsAuthorityStateChangeForDeploymentRecorded() {
		assertThat(evaluator.refreshGenerationReason(DocumentationTriggerEvent.DEPLOYMENT_RECORDED)).isEqualTo("AUTHORITY_STATE_CHANGE");
	}

	@Test
	void refreshGenerationReasonRejectsAnyOtherEvent() {
		assertThatThrownBy(() -> evaluator.refreshGenerationReason(DocumentationTriggerEvent.FULL_RELEASE_QA_FINALIZED))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private void stubEligibility(UUID candidateId, boolean eligible) {
		when(fullReleaseEligibilityValidator.validate(candidateId, FULL_RELEASE_QA_PROFILE_REF))
				.thenReturn(new FullReleaseEligibility(candidateId, eligible));
	}
}
