package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ImplementationAnchorValidatorTests {

	private final ImplementationAnchorValidator validator = new ImplementationAnchorValidator();

	private static final String PROPOSAL =
			"""
			{"localRef": "prop-a", "websitePlan": {"pages": [
			  {"localRef": "page-a-home", "sections": [{"localRef": "sec-a-hero"}]}
			]}}""";

	private static final Set<String> REPOSITORY_FILES = Set.of("src/pages/Home.tsx");

	@Test
	void passesWithFullPageAndSectionCoverage() {
		String result =
				"""
				{"implementationAnchors": [
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}
				]}""";

		assertThat(validator.validate(result, PROPOSAL, REPOSITORY_FILES).valid()).isTrue();
	}

	@Test
	void failsOnADuplicateAnchor() {
		String result =
				"""
				{"implementationAnchors": [
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}
				]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, PROPOSAL, REPOSITORY_FILES);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("duplicate anchor"));
	}

	@Test
	void failsOnAPageAnchorThatDoesNotResolve() {
		String result =
				"""
				{"implementationAnchors": [
				  {"designLocalRef": "page-does-not-exist", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}
				]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, PROPOSAL, REPOSITORY_FILES);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(
				issue -> assertThat(issue.reason()).contains("page-does-not-exist").contains("not part of the target proposal"));
	}

	@Test
	void failsOnASectionAnchorThatDoesNotResolve() {
		String result =
				"""
				{"implementationAnchors": [
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-does-not-exist", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}
				]}""";

		assertThat(validator.validate(result, PROPOSAL, REPOSITORY_FILES).valid()).isFalse();
	}

	@Test
	void anElementKindAnchorMustBeAKnownLocalRefInTheProposal() {
		String withUnknownElement =
				"""
				{"implementationAnchors": [
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]},
				  {"designLocalRef": "el-does-not-exist", "kind": "ELEMENT", "targets": [{"path": "src/pages/Home.tsx"}]}
				]}""";

		assertThat(validator.validate(withUnknownElement, PROPOSAL, REPOSITORY_FILES).valid()).isFalse();
	}

	@Test
	void failsWhenAPageHasNoAnchorAtAll() {
		String noAnchors = """
				{"implementationAnchors": []}""";

		DeveloperResultValidationResult validationResult = validator.validate(noAnchors, PROPOSAL, REPOSITORY_FILES);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("page-a-home").contains("no implementation anchor"));
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("sec-a-hero").contains("no implementation anchor"));
	}

	@Test
	void failsWhenAnAnchorTargetsAPathNotInTheFrozenRepositoryState() {
		String result =
				"""
				{"implementationAnchors": [
				  {"designLocalRef": "page-a-home", "kind": "PAGE", "targets": [{"path": "src/pages/DoesNotExist.tsx"}]},
				  {"designLocalRef": "sec-a-hero", "kind": "SECTION", "targets": [{"path": "src/pages/Home.tsx"}]}
				]}""";

		DeveloperResultValidationResult validationResult = validator.validate(result, PROPOSAL, REPOSITORY_FILES);

		assertThat(validationResult.valid()).isFalse();
		assertThat(validationResult.issues()).anySatisfy(issue -> assertThat(issue.reason()).contains("does not resolve to a real repository-relative source file"));
	}

	@Test
	void reportsMalformedJsonAsASingleIssue() {
		assertThat(validator.validate("not json", PROPOSAL, REPOSITORY_FILES).valid()).isFalse();
	}
}
