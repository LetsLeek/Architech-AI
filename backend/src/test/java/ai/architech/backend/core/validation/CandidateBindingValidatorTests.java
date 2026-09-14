package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.candidate.WebsiteImplementationCandidate;
import ai.architech.backend.core.qa.QaExecution;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateBindingValidatorTests {

	private final CandidateBindingValidator validator = new CandidateBindingValidator();

	@Test
	void aQaExecutionBoundToTheExpectedCandidateIsMatched() {
		WebsiteImplementationCandidate candidate = candidate();
		QaExecution execution = new QaExecution(UUID.randomUUID(), candidate.getId(), "profile-ref", null, "tool-profile-ref");

		CandidateBindingResult result = validator.validateCandidate(execution, candidate);

		assertThat(result.matched()).isTrue();
		assertThat(result.integrityProblem()).isNull();
	}

	@Test
	void aQaExecutionBoundToTheWrongCandidateIsDrift() {
		WebsiteImplementationCandidate intendedCandidate = candidate();
		QaExecution execution =
				new QaExecution(UUID.randomUUID(), UUID.randomUUID(), "profile-ref", null, "tool-profile-ref");

		CandidateBindingResult result = validator.validateCandidate(execution, intendedCandidate);

		assertThat(result.matched()).isFalse();
		assertThat(result.integrityProblem()).contains(intendedCandidate.getId().toString());
	}

	@Test
	void matchingExecutionSurfaceRefsAreMatched() {
		CandidateBindingResult result = validator.validateExecutionSurface("preview-42", "preview-42");

		assertThat(result.matched()).isTrue();
	}

	@Test
	void differingExecutionSurfaceRefsAreDrift() {
		CandidateBindingResult result = validator.validateExecutionSurface("preview-42", "preview-99");

		assertThat(result.matched()).isFalse();
		assertThat(result.integrityProblem()).contains("preview-42").contains("preview-99");
	}

	@Test
	void aMissingClaimedOrObservedExecutionSurfaceRefIsNeverTreatedAsDrift() {
		assertThat(validator.validateExecutionSurface(null, "preview-42").matched()).isTrue();
		assertThat(validator.validateExecutionSurface("preview-42", null).matched()).isTrue();
		assertThat(validator.validateExecutionSurface(null, null).matched()).isTrue();
	}

	private WebsiteImplementationCandidate candidate() {
		return new WebsiteImplementationCandidate(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"design-v1",
				"prop-a",
				"runtime-v1",
				"snapshot-hash-1",
				"summary",
				"[]",
				"[]",
				"[]");
	}
}
