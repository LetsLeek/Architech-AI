package ai.architech.backend.core.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.dependency.DependencyClassification;
import ai.architech.backend.core.dependency.DependencyPolicyClassifier;
import ai.architech.backend.core.dependency.DependencyPolicyFinding;
import ai.architech.backend.core.dependency.DependencyPolicyOutcome;
import ai.architech.backend.core.dependency.LockfileConsistencyChecker;
import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Fixture-driven branch coverage for every gate {@link AuthoritativeRunnerVerifierIT}'s real
 * scaffold run cannot exercise (a compliant scaffold never fails gate 1/2's policy checks, and
 * driving the real browser through every gate-10/11/12 permutation for real would be needlessly
 * slow) - collaborators are mocked so these run fast and deterministically, using a minimal real
 * zero-dependency npm project only where gate 2 must actually spawn {@code npm ci}.
 */
@ExtendWith(MockitoExtension.class)
class AuthoritativeRunnerVerifierUnitTests {

	@Mock
	private DependencyPolicyClassifier dependencyPolicyClassifier;

	@Mock
	private LockfileConsistencyChecker lockfileConsistencyChecker;

	@Mock
	private SecretScanGate secretScanGate;

	@Mock
	private HandoffFreezeGate handoffFreezeGate;

	@Mock
	private LocalRuntimeSmokeRunner localRuntimeSmokeRunner;

	@Mock
	private NetworkPolicyChecker networkPolicyChecker;

	@Mock
	private ResourceLoader resourceLoader;

	@TempDir
	Path root;

	private AuthoritativeRunnerVerifier verifier;
	private Workspace workspace;
	private FrozenHandoffSnapshot snapshot;

	private static final String FIXTURE_SCRIPTS =
			"""
			{"typecheck": "true", "lint": "true", "test": "true", "build": "true"}""";

	@BeforeEach
	void setUp() throws IOException {
		Resource scaffoldResource = mockScaffoldResource("{\"scripts\": " + FIXTURE_SCRIPTS + "}");
		lenient().when(resourceLoader.getResource(anyString())).thenReturn(scaffoldResource);
		verifier = new AuthoritativeRunnerVerifier(
				dependencyPolicyClassifier,
				lockfileConsistencyChecker,
				secretScanGate,
				handoffFreezeGate,
				localRuntimeSmokeRunner,
				networkPolicyChecker,
				resourceLoader);
		workspace = new Workspace(root);
		snapshot = new FrozenHandoffSnapshot("snapshot-hash", Instant.now());
		lenient().when(lockfileConsistencyChecker.findMismatches(any(), any())).thenReturn(List.of());
		lenient().when(dependencyPolicyClassifier.classifyDependency(anyString(), anyString()))
				.thenReturn(new DependencyPolicyFinding("pkg", "1.0.0", DependencyClassification.PLATFORM_APPROVED, DependencyPolicyOutcome.PASS, "fine"));
		lenient().when(dependencyPolicyClassifier.classifyLifecycleScripts(any())).thenReturn(List.of());
	}

	@Test
	void failsGate1WhenTheManifestOrLockfileIsMissing() {
		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).singleElement().satisfies(gate -> assertThat(gate.gateName()).contains("repository-dependency-integrity"));
	}

	@Test
	void failsGate1OnALockfileMismatch() throws IOException {
		writeFixturePackageJsonAndLockfile();
		when(lockfileConsistencyChecker.findMismatches(any(), any())).thenReturn(List.of("'left-pad' is declared but not resolved"));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("repository-dependency-integrity");
			assertThat(gate.detail()).contains("left-pad");
		});
	}

	@Test
	void failsGate1WhenARequiredScriptWasHollowedOut() throws IOException {
		writePackageJson("{\"scripts\": {\"typecheck\": \"echo ok\", \"lint\": \"true\", \"test\": \"true\", \"build\": \"true\"}, \"dependencies\": {}, \"devDependencies\": {}}");
		writeMinimalLockfile();

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("repository-dependency-integrity");
			assertThat(gate.detail()).contains("typecheck");
		});
	}

	@Test
	void failsGate2WhenADependencyIsPolicyBlocked() throws IOException {
		writePackageJson("{\"scripts\": " + FIXTURE_SCRIPTS + ", \"dependencies\": {\"evil-pkg\": \"git+https://evil\"}, \"devDependencies\": {}}");
		writeMinimalLockfile();
		when(dependencyPolicyClassifier.classifyDependency(anyString(), anyString()))
				.thenReturn(new DependencyPolicyFinding("evil-pkg", "git+https://evil", DependencyClassification.PROHIBITED, DependencyPolicyOutcome.BLOCK, "git source is prohibited"));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("clean-policy-compliant-install");
			assertThat(gate.detail()).contains("git source is prohibited");
		});
	}

	@Test
	void failsGate2WhenALifecycleScriptIsBlocked() throws IOException {
		writeFixturePackageJsonAndLockfile();
		when(dependencyPolicyClassifier.classifyLifecycleScripts(any()))
				.thenReturn(List.of(new DependencyPolicyFinding("<manifest lifecycle script>", "postinstall", DependencyClassification.PROHIBITED, DependencyPolicyOutcome.BLOCK, "lifecycle scripts are disabled")));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> assertThat(gate.detail()).contains("lifecycle scripts are disabled"));
	}

	@Test
	void passesGatesOneAndTwoWithARealMinimalZeroDependencyInstall() throws IOException, InterruptedException {
		writeFixturePackageJsonAndLockfile();
		when(localRuntimeSmokeRunner.run(workspace)).thenReturn(LocalRuntimeSmokeOutcome.startupFailed("stubbed - not under test here"));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.gates()).extracting(GateResult::gateName)
				.contains("repository-dependency-integrity (gate 1)", "clean-policy-compliant-install (gate 2)", "typecheck (gate 3)");
		assertThat(result.gates()).filteredOn(gate -> gate.gateName().startsWith("repository-dependency-integrity")
						|| gate.gateName().startsWith("clean-policy-compliant-install"))
				.allSatisfy(gate -> assertThat(gate.outcome()).isEqualTo(VerificationOutcome.PASS));
	}

	@Test
	void localRuntimeStartupFailureStopsBeforeAnyOtherLocalRuntimeGate() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace)).thenReturn(LocalRuntimeSmokeOutcome.startupFailed("dev server never came up"));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.ERROR);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("local-runtime-startup");
			assertThat(gate.outcome()).isEqualTo(VerificationOutcome.ERROR);
		});
	}

	@Test
	void aFailedCanonicalRouteStopsBeforeNavigationAndLaterGates() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace))
				.thenReturn(new LocalRuntimeSmokeOutcome(true, null, false, false, false, List.of(), List.of()));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> assertThat(gate.gateName()).contains("canonical-route-smoke"));
	}

	@Test
	void aFailedNavigationStopsBeforeRuntimeIntegrityAndLaterGates() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace)).thenReturn(new LocalRuntimeSmokeOutcome(
				true, null, true, false, false, List.of(), List.of(new BrowserRuntimeIssue("navigation", "route '/about' did not load"))));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("primary-navigation-smoke");
			assertThat(gate.detail()).contains("/about");
		});
	}

	@Test
	void aFatalRuntimeIssueFailsBrowserRuntimeIntegrityBeforeResponsiveGates() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace)).thenReturn(new LocalRuntimeSmokeOutcome(
				true, null, true, false, false, List.of(), List.of(new BrowserRuntimeIssue("page-error", "boom"))));
		when(networkPolicyChecker.classify(any(), any(), any())).thenReturn(List.of());

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("browser-runtime-integrity");
			assertThat(gate.detail()).contains("page-error: boom");
		});
	}

	@Test
	void aBlockedNetworkFindingFailsBrowserRuntimeIntegrity() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace))
				.thenReturn(new LocalRuntimeSmokeOutcome(true, null, true, false, false, List.of(new ObservedRequest("https://cdn.evil.example/x.js", "script")), List.of()));
		when(networkPolicyChecker.classify(any(), any(), any()))
				.thenReturn(List.of(new NetworkPolicyFinding("https://cdn.evil.example/x.js", "cdn.evil.example", NetworkPolicyOutcome.BLOCKED, "undeclared external runtime target")));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("browser-runtime-integrity");
			assertThat(gate.detail()).contains("cdn.evil.example");
		});
	}

	@Test
	void aWideViewportOverflowStopsBeforeTheNarrowGate() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace))
				.thenReturn(new LocalRuntimeSmokeOutcome(true, null, true, true, true, List.of(), List.of()));
		when(networkPolicyChecker.classify(any(), any(), any())).thenReturn(List.of());

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> assertThat(gate.gateName()).contains("wide-responsive-sanity"));
	}

	@Test
	void aNarrowViewportOverflowFailsAfterWidePasses() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace))
				.thenReturn(new LocalRuntimeSmokeOutcome(true, null, true, false, true, List.of(), List.of()));
		when(networkPolicyChecker.classify(any(), any(), any())).thenReturn(List.of());

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> assertThat(gate.gateName()).contains("narrow-responsive-sanity"));
	}

	@Test
	void aCleanSmokeReachesSecretScanAndFinalIntegrity() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace))
				.thenReturn(new LocalRuntimeSmokeOutcome(true, null, true, false, false, List.of(), List.of()));
		when(networkPolicyChecker.classify(any(), any(), any())).thenReturn(List.of());
		when(secretScanGate.scan(any())).thenReturn(new SecretScanResult(List.of()));
		when(handoffFreezeGate.matchesCurrentState(workspace, snapshot)).thenReturn(true);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.passed()).as(result.gates().toString()).isTrue();
		assertThat(result.gates()).extracting(GateResult::gateName)
				.contains("secret-credential-scan (gate 13)", "final-source-state-integrity (gate 14)");
	}

	@Test
	void aSecretFindingFailsGateThirteen() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace))
				.thenReturn(new LocalRuntimeSmokeOutcome(true, null, true, false, false, List.of(), List.of()));
		when(networkPolicyChecker.classify(any(), any(), any())).thenReturn(List.of());
		when(secretScanGate.scan(any())).thenReturn(new SecretScanResult(List.of(new SecretFinding("src/config.ts", "AWS_ACCESS_KEY", 3))));

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> {
			assertThat(gate.gateName()).contains("secret-credential-scan");
			assertThat(gate.detail()).contains("AWS_ACCESS_KEY");
		});
	}

	@Test
	void aMismatchedFinalSnapshotFailsGateFourteen() throws IOException {
		passGatesOneThroughSixByStubbingProjectExecution();
		when(localRuntimeSmokeRunner.run(workspace))
				.thenReturn(new LocalRuntimeSmokeOutcome(true, null, true, false, false, List.of(), List.of()));
		when(networkPolicyChecker.classify(any(), any(), any())).thenReturn(List.of());
		when(secretScanGate.scan(any())).thenReturn(new SecretScanResult(List.of()));
		when(handoffFreezeGate.matchesCurrentState(workspace, snapshot)).thenReturn(false);

		RunnerVerificationResult result = verifier.verify(workspace, snapshot, List.of());

		assertThat(result.outcome()).isEqualTo(VerificationOutcome.FAIL);
		assertThat(result.gates()).last().satisfies(gate -> assertThat(gate.gateName()).contains("final-source-state-integrity"));
	}

	/** Gates 1-6 all real except the process-spawning ones are backed by a trivial zero-dependency npm project. */
	private void passGatesOneThroughSixByStubbingProjectExecution() throws IOException {
		writeFixturePackageJsonAndLockfile();
	}

	private void writeFixturePackageJsonAndLockfile() throws IOException {
		writePackageJson("{\"scripts\": " + FIXTURE_SCRIPTS + ", \"dependencies\": {}, \"devDependencies\": {}}");
		writeMinimalLockfile();
	}

	private void writePackageJson(String content) throws IOException {
		Files.writeString(root.resolve("package.json"), content, StandardCharsets.UTF_8);
	}

	private void writeMinimalLockfile() throws IOException {
		Files.writeString(
				root.resolve("package-lock.json"),
				"""
				{"name": "fixture", "version": "1.0.0", "lockfileVersion": 3, "requires": true, "packages": {"": {"name": "fixture", "version": "1.0.0"}}}""",
				StandardCharsets.UTF_8);
	}

	private Resource mockScaffoldResource(String content) throws IOException {
		Resource resource = org.mockito.Mockito.mock(Resource.class);
		lenient().when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(content);
		lenient().when(resource.getInputStream())
				.thenAnswer(invocation -> new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
		return resource;
	}
}
