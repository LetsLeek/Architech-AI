package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalRefUniquenessValidatorTests {

	private final LocalRefUniquenessValidator validator = new LocalRefUniquenessValidator();

	@Test
	void acceptsAllDistinctLocalRefs() {
		LocalRefValidationResult result = validator.validate(List.of("loc-1", "loc-2", "loc-3"));

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsADuplicatedLocalRef() {
		LocalRefValidationResult result = validator.validate(List.of("loc-1", "loc-2", "loc-1"));

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).extracting(LocalRefValidationIssue::localRef).containsExactly("loc-1");
	}

	@Test
	void acceptsAnEmptyList() {
		assertThat(validator.validate(List.of()).valid()).isTrue();
	}

	@Test
	void extractsLocalRefsFromNestedCandidateJsonAndAcceptsDistinctOnes() {
		String json =
				"""
				{"locations": [{"localRef": "loc-1"}], "offerings": [{"localRef": "loc-2"}]}
				""";

		LocalRefValidationResult result = validator.validate(json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void extractsLocalRefsFromNestedCandidateJsonAndRejectsADuplicate() {
		String json =
				"""
				{"locations": [{"localRef": "loc-1"}], "offerings": [{"localRef": "loc-1"}]}
				""";

		LocalRefValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).extracting(LocalRefValidationIssue::localRef).containsExactly("loc-1");
	}

	@Test
	void acceptsDistinctLocalRefsAcrossAllProposalsOfADesignProposalSetCandidate() {
		// AIW-122: "localRef values are globally unique within the complete design-proposal-set
		// artifact" - the exact same generic walk already used for customer-profile/
		// website-requirements, needing no new code for this specific invariant.
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [{"localRef": "page-1"}]}},
				  {"localRef": "prop-b", "websitePlan": {"pages": [{"localRef": "page-2"}]}}
				]}
				""";

		LocalRefValidationResult result = validator.validate(json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsALocalRefReusedAcrossTwoDifferentProposalsOfADesignProposalSetCandidate() {
		// Global uniqueness spans the whole artifact, not per-proposal - the same localRef value
		// used by two different proposals must fail here even though each proposal is otherwise
		// independently valid.
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a", "websitePlan": {"pages": [{"localRef": "shared-ref"}]}},
				  {"localRef": "prop-b", "websitePlan": {"pages": [{"localRef": "shared-ref"}]}}
				]}
				""";

		LocalRefValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).extracting(LocalRefValidationIssue::localRef).containsExactly("shared-ref");
	}

	@Test
	void reportsUnparseableCandidateJsonAsAFailureRatherThanThrowing() {
		LocalRefValidationResult result = validator.validate("not json {{{");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}
}
