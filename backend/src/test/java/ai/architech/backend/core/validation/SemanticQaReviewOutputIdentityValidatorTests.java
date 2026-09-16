package ai.architech.backend.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticQaReviewOutputIdentityValidatorTests {

	private final SemanticQaReviewOutputIdentityValidator validator = new SemanticQaReviewOutputIdentityValidator();

	private static final String INPUT = """
			{
			  "qaExecutionRef": "qa-exec-101",
			  "target": {"candidateRef": "candidate-42"},
			  "qaAuthority": {"qaProfileRef": "website-qa-full-release@1.0.0"},
			  "provenance": {"inputSnapshotRef": "snapshot-101"}
			}
			""";

	@Test
	void aMatchingResultHasNoIssues() {
		String result = """
				{
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "snapshot-101"
				}
				""";

		assertThat(validator.validate(result, INPUT)).isEmpty();
	}

	@Test
	void reportsAMismatchedQaProfileRef() {
		String result = """
				{
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "candidate-42",
				  "qaProfileRef": "website-qa-comparison-readiness@1.0.0",
				  "inputSnapshotRef": "snapshot-101"
				}
				""";

		List<SemanticQaReviewOutputIssue> issues = validator.validate(result, INPUT);

		assertThat(issues).containsExactly(new SemanticQaReviewOutputIssue(
				"qaProfileRef", "result claims 'website-qa-comparison-readiness@1.0.0' but this execution's own input claims 'website-qa-full-release@1.0.0'"));
	}

	@Test
	void reportsAMismatchedTestedCandidateRef() {
		String result = """
				{
				  "qaExecutionRef": "qa-exec-101",
				  "testedCandidateRef": "some-other-candidate",
				  "qaProfileRef": "website-qa-full-release@1.0.0",
				  "inputSnapshotRef": "snapshot-101"
				}
				""";

		assertThat(validator.validate(result, INPUT)).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("testedCandidateRef"));
	}

	@Test
	void reportsAllMismatchesAtOnce() {
		String result = """
				{
				  "qaExecutionRef": "different-exec",
				  "testedCandidateRef": "different-candidate",
				  "qaProfileRef": "different-profile",
				  "inputSnapshotRef": "different-snapshot"
				}
				""";

		assertThat(validator.validate(result, INPUT)).extracting(SemanticQaReviewOutputIssue::path)
				.containsExactlyInAnyOrder("qaExecutionRef", "testedCandidateRef", "qaProfileRef", "inputSnapshotRef");
	}

	@Test
	void treatsUnparseableJsonAsASingleIssue() {
		assertThat(validator.validate("not json {{{", INPUT)).hasSize(1);
	}
}
