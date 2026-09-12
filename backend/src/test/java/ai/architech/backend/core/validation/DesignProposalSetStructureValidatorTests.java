package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DesignProposalSetStructureValidatorTests {

	private final DesignProposalSetStructureValidator validator = new DesignProposalSetStructureValidator();

	@Test
	void acceptsAValidProposalWithResolvingPageSectionAndPatternRefs() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [
				     {"localRef": "page-home", "route": "/", "sections": [
				       {"localRef": "sec-hero", "elements": [
				         {"localRef": "el-1", "patternRef": "pattern-btn",
				          "target": {"type": "section", "pageRef": "page-home", "sectionRef": "sec-hero"}}
				       ]}
				     ]}
				   ]},
				   "designSpecification": {"uiPatterns": [{"localRef": "pattern-btn"}]}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void rejectsAProposalWithNoRootRoute() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [{"localRef": "page-1", "route": "/about", "sections": []}]},
				   "designSpecification": {"uiPatterns": []}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("exactly one root route"));
	}

	@Test
	void rejectsAProposalWithTwoRootRoutes() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [
				     {"localRef": "page-1", "route": "/", "sections": []},
				     {"localRef": "page-2", "route": "/", "sections": []}
				   ]},
				   "designSpecification": {"uiPatterns": []}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("exactly one root route") && issue.reason().contains("found 2"));
	}

	@Test
	void rejectsDuplicateNonRootRoutesWithinAProposal() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [
				     {"localRef": "page-home", "route": "/", "sections": []},
				     {"localRef": "page-1", "route": "/about", "sections": []},
				     {"localRef": "page-2", "route": "/about", "sections": []}
				   ]},
				   "designSpecification": {"uiPatterns": []}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("route '/about' used 2 times"));
	}

	@Test
	void rejectsAPageRefThatDoesNotResolveInTheSameProposal() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [
				     {"localRef": "page-home", "route": "/", "sections": [
				       {"localRef": "sec-1", "elements": [
				         {"localRef": "el-1", "target": {"type": "page", "pageRef": "does-not-exist"}}
				       ]}
				     ]}
				   ]},
				   "designSpecification": {"uiPatterns": []}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("pageRef 'does-not-exist' does not resolve"));
	}

	@Test
	void rejectsASectionRefThatBelongsToADifferentPageThanItsOwnPageRef() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [
				     {"localRef": "page-home", "route": "/", "sections": [{"localRef": "sec-home", "elements": []}]},
				     {"localRef": "page-about", "route": "/about", "sections": [
				       {"localRef": "sec-1", "elements": [
				         {"localRef": "el-1", "target": {"type": "section", "pageRef": "page-home", "sectionRef": "sec-1"}}
				       ]}
				     ]}
				   ]},
				   "designSpecification": {"uiPatterns": []}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues())
				.anyMatch(issue -> issue.reason().contains("sectionRef 'sec-1' does not resolve to a section under page 'page-home'"));
	}

	@Test
	void rejectsAPatternRefThatDoesNotResolveInTheSameProposal() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [
				     {"localRef": "page-home", "route": "/", "sections": [
				       {"localRef": "sec-1", "elements": [{"localRef": "el-1", "patternRef": "missing-pattern"}]}
				     ]}
				   ]},
				   "designSpecification": {"uiPatterns": [{"localRef": "pattern-btn"}]}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch(issue -> issue.reason().contains("patternRef 'missing-pattern' does not resolve"));
	}

	@Test
	void rejectsALocalReferenceThatOnlyResolvesInADifferentProposal() {
		String json =
				"""
				{"proposals": [
				  {"localRef": "prop-a",
				   "websitePlan": {"pages": [
				     {"localRef": "page-home", "route": "/", "sections": [
				       {"localRef": "sec-1", "elements": [
				         {"localRef": "el-1", "target": {"type": "page", "pageRef": "prop-b-page"}}
				       ]}
				     ]}
				   ]},
				   "designSpecification": {"uiPatterns": []}
				  },
				  {"localRef": "prop-b",
				   "websitePlan": {"pages": [{"localRef": "prop-b-page", "route": "/", "sections": []}]},
				   "designSpecification": {"uiPatterns": []}
				  }
				]}
				""";

		DesignProposalSetValidationResult result = validator.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.issues())
				.anyMatch(issue -> "prop-a".equals(issue.proposalRef()) && issue.reason().contains("pageRef 'prop-b-page' does not resolve"));
	}

	@Test
	void reportsUnparseableCandidateJsonAsAFailureRatherThanThrowing() {
		DesignProposalSetValidationResult result = validator.validate("not json {{{");

		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).isNotEmpty();
	}
}
