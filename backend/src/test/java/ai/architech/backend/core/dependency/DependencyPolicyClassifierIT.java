package ai.architech.backend.core.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Covers every scenario AIW-140's acceptance criteria names: approved existing package, approved
 * new package, prohibited package/source, lifecycle-script denial. Lockfile-mismatch and
 * no-compliant-alternative-blocker scenarios are covered by {@link LockfileConsistencyCheckerTests}
 * and {@link #prohibitedDependencyWithNoPlatformApprovedAlternativeBlocksTheInstall()} respectively.
 */
@SpringBootTest
class DependencyPolicyClassifierIT {

	@Autowired
	private DependencyPolicyClassifier classifier;

	@Test
	void classifiesAnApprovedExistingPackageAsPlatformApprovedAndPass() {
		// "react" is one of the Development Base scaffold's own dependencies (AIW-138).
		DependencyPolicyFinding finding = classifier.classifyDependency("react", "^19.2.8");

		assertThat(finding.classification()).isEqualTo(DependencyClassification.PLATFORM_APPROVED);
		assertThat(finding.outcome()).isEqualTo(DependencyPolicyOutcome.PASS);
	}

	@Test
	void classifiesAnApprovedNewPackageAsPolicyEligibleAndPass() {
		// Not part of the scaffold, but a normal pinned registry-sourced version spec.
		DependencyPolicyFinding finding = classifier.classifyDependency("date-fns", "^4.1.0");

		assertThat(finding.classification()).isEqualTo(DependencyClassification.POLICY_ELIGIBLE);
		assertThat(finding.outcome()).isEqualTo(DependencyPolicyOutcome.PASS);
	}

	@Test
	void classifiesAnUnpinnedRangeAsPolicyEligibleWithAWarning() {
		DependencyPolicyFinding finding = classifier.classifyDependency("some-lib", "*");

		assertThat(finding.classification()).isEqualTo(DependencyClassification.POLICY_ELIGIBLE);
		assertThat(finding.outcome()).isEqualTo(DependencyPolicyOutcome.WARN);
	}

	@Test
	void classifiesAGitSourcedDependencyAsProhibitedAndBlock() {
		DependencyPolicyFinding finding =
				classifier.classifyDependency("some-lib", "git+https://github.com/someone/some-lib.git");

		assertThat(finding.classification()).isEqualTo(DependencyClassification.PROHIBITED);
		assertThat(finding.outcome()).isEqualTo(DependencyPolicyOutcome.BLOCK);
	}

	@Test
	void classifiesATarballUrlDependencyAsProhibitedAndBlock() {
		DependencyPolicyFinding finding =
				classifier.classifyDependency("some-lib", "https://example.com/some-lib-1.0.0.tgz");

		assertThat(finding.classification()).isEqualTo(DependencyClassification.PROHIBITED);
		assertThat(finding.outcome()).isEqualTo(DependencyPolicyOutcome.BLOCK);
	}

	@Test
	void classifiesAManuallyVendoredFileDependencyAsProhibitedAndBlock() {
		DependencyPolicyFinding finding = classifier.classifyDependency("some-lib", "file:../vendor/some-lib");

		assertThat(finding.classification()).isEqualTo(DependencyClassification.PROHIBITED);
		assertThat(finding.outcome()).isEqualTo(DependencyPolicyOutcome.BLOCK);
	}

	@Test
	void deniesLifecycleScriptsWithNoExceptionInV1() {
		var findings = classifier.classifyLifecycleScripts(Map.of(
				"build", "vite build",
				"postinstall", "node ./run-something.js"));

		assertThat(findings).hasSize(1);
		assertThat(findings.getFirst().outcome()).isEqualTo(DependencyPolicyOutcome.BLOCK);
		assertThat(findings.getFirst().versionSpec()).isEqualTo("postinstall");
	}

	@Test
	void ordinaryProjectScriptsAreNeverFlaggedAsLifecycleScripts() {
		var findings = classifier.classifyLifecycleScripts(
				Map.of("build", "vite build", "test", "vitest run", "dev", "vite", "lint", "oxlint"));

		assertThat(findings).isEmpty();
	}

	@Test
	void prohibitedDependencyWithNoPlatformApprovedAlternativeBlocksTheInstall() {
		// Demonstrates the data a DEVELOPER_BLOCKER / DEPENDENCY_POLICY_BLOCKED decision (owned
		// upstream by the agent/Runner, not this classifier) would be based on: a genuinely
		// prohibited source, for a package that is not already part of the platform-approved set -
		// i.e. no compliant authorized alternative is already sitting in the Development Base.
		DependencyPolicyFinding finding = classifier.classifyDependency("left-pad", "github:foo/left-pad");
		DependencyPolicyResult result = new DependencyPolicyResult(List.of(finding));

		assertThat(result.blocked()).isTrue();
		assertThat(finding.reason()).isNotBlank();
	}
}
