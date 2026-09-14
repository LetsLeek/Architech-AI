package ai.architech.backend.core.verification;

import ai.architech.backend.core.dependency.DependencyPolicyClassifier;
import ai.architech.backend.core.dependency.DependencyPolicyFinding;
import ai.architech.backend.core.dependency.DependencyPolicyOutcome;
import ai.architech.backend.core.dependency.LockfileConsistencyChecker;
import ai.architech.backend.core.handoff.FrozenHandoffSnapshot;
import ai.architech.backend.core.handoff.HandoffFreezeGate;
import ai.architech.backend.core.sandbox.ProcessResult;
import ai.architech.backend.core.sandbox.ProjectExecutionCapability;
import ai.architech.backend.core.sandbox.ProjectExecutionTask;
import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Authoritative final Website Developer Runner Verification against the exact frozen Developer
 * handoff state (AIW-143). Runs a fixed sequence of all 14 mandatory gates and stops at the
 * first non-{@code PASS} one (a technical-source or infrastructure failure cascading into every
 * later gate would just repeat the same underlying cause with no new information) - see {@link
 * RunnerVerificationResult} for how the overall outcome is computed from whichever gates
 * actually ran.
 *
 * <p>Gates 1-6, 13 and 14 are pure filesystem/process checks against infrastructure already in
 * this codebase (dependency policy, lockfile consistency, the scaffold's own npm scripts, secret
 * scanning, the frozen handoff snapshot). Gates 7-12 drive a real headless browser via {@link
 * LocalRuntimeSmokeRunner} against the Developer execution's own locally-started dev server, and
 * classify what it observed via {@link NetworkPolicyChecker} - {@code authorizedExternalTargets}
 * is whatever the execution's own Runtime Profile/asset/Integration Contract authorization
 * actually allows (empty in V1, since {@code integrationContext.integrationContracts} is always
 * empty per AIW-151's assembler).
 */
@Component
public class AuthoritativeRunnerVerifier {

	/**
	 * The exact 14 mandatory gate names, in the exact order {@link #verify} runs them - the same
	 * strings every {@link GateResult#gateName()} this class ever produces uses. Exposed so a
	 * persistence layer (AIW-155) can tell a gate that is genuinely absent from a fail-fast run's
	 * {@link RunnerVerificationResult#gates()} (skipped because an earlier gate already stopped
	 * the run) from one that actually executed, without hard-coding a second copy of these names
	 * that could silently drift from this class's own.
	 */
	public static final List<String> MANDATORY_GATE_NAMES = List.of(
			"repository-dependency-integrity (gate 1)",
			"clean-policy-compliant-install (gate 2)",
			"typecheck (gate 3)",
			"lint (gate 4)",
			"test (gate 5)",
			"build (gate 6)",
			"local-runtime-startup (gate 7)",
			"canonical-route-smoke (gate 8)",
			"primary-navigation-smoke (gate 9)",
			"browser-runtime-integrity (gate 10)",
			"wide-responsive-sanity (gate 11)",
			"narrow-responsive-sanity (gate 12)",
			"secret-credential-scan (gate 13)",
			"final-source-state-integrity (gate 14)");

	private static final String SCAFFOLD_PACKAGE_JSON =
			"classpath:project-types/website/agents/developer-agent/scaffold/package.json";
	private static final List<String> REQUIRED_SCRIPTS = List.of("typecheck", "lint", "test", "build");
	// Matches LocalRuntimeSmokeRunner's own BASE_URL host exactly - see that class for why the
	// literal IPv4 address, not "localhost", is what requests actually observe as the self-origin host.
	private static final String LOCAL_RUNTIME_HOST = "127.0.0.1";

	private final DependencyPolicyClassifier dependencyPolicyClassifier;
	private final LockfileConsistencyChecker lockfileConsistencyChecker;
	private final SecretScanGate secretScanGate;
	private final HandoffFreezeGate handoffFreezeGate;
	private final LocalRuntimeSmokeRunner localRuntimeSmokeRunner;
	private final NetworkPolicyChecker networkPolicyChecker;
	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper = new ObjectMapper();

	AuthoritativeRunnerVerifier(
			DependencyPolicyClassifier dependencyPolicyClassifier,
			LockfileConsistencyChecker lockfileConsistencyChecker,
			SecretScanGate secretScanGate,
			HandoffFreezeGate handoffFreezeGate,
			LocalRuntimeSmokeRunner localRuntimeSmokeRunner,
			NetworkPolicyChecker networkPolicyChecker,
			ResourceLoader resourceLoader) {
		this.dependencyPolicyClassifier = dependencyPolicyClassifier;
		this.lockfileConsistencyChecker = lockfileConsistencyChecker;
		this.secretScanGate = secretScanGate;
		this.handoffFreezeGate = handoffFreezeGate;
		this.localRuntimeSmokeRunner = localRuntimeSmokeRunner;
		this.networkPolicyChecker = networkPolicyChecker;
		this.resourceLoader = resourceLoader;
	}

	public RunnerVerificationResult verify(
			Workspace workspace,
			FrozenHandoffSnapshot snapshot,
			List<AuthorizedExternalTarget> authorizedExternalTargets) {
		List<GateResult> gates = new ArrayList<>();

		if (!runGate(gates, () -> repositoryDependencyIntegrityGate(workspace))) {
			return new RunnerVerificationResult(gates);
		}
		if (!runGate(gates, () -> cleanPolicyCompliantInstallGate(workspace))) {
			return new RunnerVerificationResult(gates);
		}
		if (!runGate(gates, () -> projectExecutionGate(workspace, "typecheck (gate 3)", ProjectExecutionTask.TYPECHECK))) {
			return new RunnerVerificationResult(gates);
		}
		if (!runGate(gates, () -> projectExecutionGate(workspace, "lint (gate 4)", ProjectExecutionTask.LINT))) {
			return new RunnerVerificationResult(gates);
		}
		if (!runGate(gates, () -> projectExecutionGate(workspace, "test (gate 5)", ProjectExecutionTask.TEST))) {
			return new RunnerVerificationResult(gates);
		}
		if (!runGate(gates, () -> projectExecutionGate(workspace, "build (gate 6)", ProjectExecutionTask.BUILD))) {
			return new RunnerVerificationResult(gates);
		}

		LocalRuntimeSmokeOutcome smoke = localRuntimeSmokeRunner.run(workspace);
		if (!appendLocalRuntimeGates(gates, smoke, authorizedExternalTargets)) {
			return new RunnerVerificationResult(gates);
		}

		if (!runGate(gates, () -> secretCredentialScanGate(workspace))) {
			return new RunnerVerificationResult(gates);
		}
		runGate(gates, () -> finalSourceStateIntegrityGate(workspace, snapshot));

		return new RunnerVerificationResult(gates);
	}

	/** Returns {@code false} the moment any of gates 7-12 is not PASS, matching every other gate's fail-fast contract. */
	private boolean appendLocalRuntimeGates(
			List<GateResult> gates, LocalRuntimeSmokeOutcome smoke, List<AuthorizedExternalTarget> authorizedExternalTargets) {
		if (!smoke.startedSuccessfully()) {
			gates.add(GateResult.error("local-runtime-startup (gate 7)", smoke.startupFailureDetail()));
			return false;
		}
		gates.add(GateResult.pass("local-runtime-startup (gate 7)"));

		GateResult routeGate = smoke.canonicalRouteLoaded()
				? GateResult.pass("canonical-route-smoke (gate 8)")
				: GateResult.fail("canonical-route-smoke (gate 8)", "canonical route '/' did not load successfully");
		gates.add(routeGate);
		if (routeGate.outcome() != VerificationOutcome.PASS) {
			return false;
		}

		List<String> navigationFailures = smoke.runtimeIssues().stream()
				.filter(issue -> "navigation".equals(issue.source()))
				.map(BrowserRuntimeIssue::message)
				.toList();
		GateResult navigationGate = navigationFailures.isEmpty()
				? GateResult.pass("primary-navigation-smoke (gate 9)")
				: GateResult.fail("primary-navigation-smoke (gate 9)", String.join("; ", navigationFailures));
		gates.add(navigationGate);
		if (navigationGate.outcome() != VerificationOutcome.PASS) {
			return false;
		}

		GateResult runtimeIntegrityGate = browserRuntimeIntegrityGate(smoke, authorizedExternalTargets);
		gates.add(runtimeIntegrityGate);
		if (runtimeIntegrityGate.outcome() != VerificationOutcome.PASS) {
			return false;
		}

		GateResult wideGate = smoke.wideViewportOverflow()
				? GateResult.fail("wide-responsive-sanity (gate 11)", "horizontal overflow detected at the wide viewport")
				: GateResult.pass("wide-responsive-sanity (gate 11)");
		gates.add(wideGate);
		if (wideGate.outcome() != VerificationOutcome.PASS) {
			return false;
		}

		GateResult narrowGate = smoke.narrowViewportOverflow()
				? GateResult.fail("narrow-responsive-sanity (gate 12)", "horizontal overflow detected at the narrow viewport")
				: GateResult.pass("narrow-responsive-sanity (gate 12)");
		gates.add(narrowGate);
		return narrowGate.outcome() == VerificationOutcome.PASS;
	}

	private GateResult browserRuntimeIntegrityGate(
			LocalRuntimeSmokeOutcome smoke, List<AuthorizedExternalTarget> authorizedExternalTargets) {
		String gateName = "browser-runtime-integrity (gate 10)";
		List<String> problems = new ArrayList<>();

		smoke.runtimeIssues().stream()
				.filter(issue -> !"navigation".equals(issue.source()))
				.forEach(issue -> problems.add(issue.source() + ": " + issue.message()));

		networkPolicyChecker.classify(LOCAL_RUNTIME_HOST, authorizedExternalTargets, smoke.observedRequests()).stream()
				.filter(finding -> finding.outcome() == NetworkPolicyOutcome.BLOCKED)
				.forEach(finding -> problems.add(finding.url() + " - " + finding.reason()));

		if (!problems.isEmpty()) {
			return GateResult.fail(gateName, String.join("; ", problems));
		}
		return GateResult.pass(gateName);
	}

	private boolean runGate(List<GateResult> gates, java.util.function.Supplier<GateResult> gate) {
		GateResult result = gate.get();
		gates.add(result);
		return result.outcome() == VerificationOutcome.PASS;
	}

	private GateResult repositoryDependencyIntegrityGate(Workspace workspace) {
		String gateName = "repository-dependency-integrity (gate 1)";
		JsonNode packageJson;
		JsonNode lockfile;
		try {
			packageJson = readJson(workspace.root().resolve("package.json"));
			lockfile = readJson(workspace.root().resolve("package-lock.json"));
		} catch (UncheckedIOException e) {
			return GateResult.fail(gateName, "required manifest/lockfile missing or unreadable: " + e.getMessage());
		}

		Map<String, String> manifestDependencies = allDependencies(packageJson);
		List<String> mismatches = lockfileConsistencyChecker.findMismatches(manifestDependencies, lockfile.path("packages"));
		if (!mismatches.isEmpty()) {
			return GateResult.fail(gateName, String.join("; ", mismatches));
		}

		List<String> scriptIssues = scriptIntegrityIssues(packageJson);
		if (!scriptIssues.isEmpty()) {
			return GateResult.fail(gateName, String.join("; ", scriptIssues));
		}

		return GateResult.pass(gateName);
	}

	private GateResult cleanPolicyCompliantInstallGate(Workspace workspace) {
		String gateName = "clean-policy-compliant-install (gate 2)";
		JsonNode packageJson;
		byte[] lockfileBefore;
		try {
			packageJson = readJson(workspace.root().resolve("package.json"));
			lockfileBefore = Files.readAllBytes(workspace.root().resolve("package-lock.json"));
		} catch (UncheckedIOException | IOException e) {
			return GateResult.fail(gateName, "required manifest/lockfile missing or unreadable: " + e.getMessage());
		}

		List<String> blocked = new ArrayList<>();
		allDependencies(packageJson)
				.forEach((name, versionSpec) -> {
					DependencyPolicyFinding finding = dependencyPolicyClassifier.classifyDependency(name, versionSpec);
					if (finding.outcome() == DependencyPolicyOutcome.BLOCK) {
						blocked.add(finding.reason());
					}
				});
		Map<String, String> scripts = new LinkedHashMap<>();
		JsonNode scriptsNode = packageJson.path("scripts");
		scriptsNode.propertyNames().forEach(name -> scripts.put(name, scriptsNode.path(name).asString()));
		dependencyPolicyClassifier.classifyLifecycleScripts(scripts).forEach(finding -> blocked.add(finding.reason()));
		if (!blocked.isEmpty()) {
			return GateResult.fail(gateName, String.join("; ", blocked));
		}

		try {
			ProcessResult installResult = new ProjectExecutionCapability(workspace).run(ProjectExecutionTask.INSTALL);
			if (!installResult.succeeded()) {
				return GateResult.fail(gateName, "npm ci exited " + installResult.exitCode() + ": " + installResult.stderr());
			}
		} catch (UncheckedIOException | IllegalStateException e) {
			return GateResult.error(gateName, "sandbox failed to run the install task: " + e.getMessage());
		}

		byte[] lockfileAfter;
		try {
			lockfileAfter = Files.readAllBytes(workspace.root().resolve("package-lock.json"));
		} catch (IOException e) {
			return GateResult.error(gateName, "failed to re-read the lockfile after install: " + e.getMessage());
		}
		if (!Arrays.equals(lockfileBefore, lockfileAfter)) {
			return GateResult.fail(gateName, "install mutated package-lock.json - not reproducible from the committed lockfile alone");
		}

		return GateResult.pass(gateName);
	}

	private GateResult projectExecutionGate(Workspace workspace, String gateName, ProjectExecutionTask task) {
		try {
			ProcessResult result = new ProjectExecutionCapability(workspace).run(task);
			if (!result.succeeded()) {
				return GateResult.fail(gateName, "exited " + result.exitCode() + ": " + result.stderr());
			}
			return GateResult.pass(gateName);
		} catch (UncheckedIOException | IllegalStateException e) {
			return GateResult.error(gateName, "sandbox failed to run this task: " + e.getMessage());
		}
	}

	private GateResult secretCredentialScanGate(Workspace workspace) {
		String gateName = "secret-credential-scan (gate 13)";
		try {
			SecretScanResult result = secretScanGate.scan(workspace.root());
			if (result.blocked()) {
				String detail = result.findings().stream()
						.map(finding -> finding.filePath() + ":" + finding.lineNumber() + " " + finding.patternName())
						.collect(Collectors.joining("; "));
				return GateResult.fail(gateName, detail);
			}
			return GateResult.pass(gateName);
		} catch (RuntimeException e) {
			return GateResult.error(gateName, "secret scan tooling failed: " + e.getMessage());
		}
	}

	private GateResult finalSourceStateIntegrityGate(Workspace workspace, FrozenHandoffSnapshot snapshot) {
		String gateName = "final-source-state-integrity (gate 14)";
		try {
			if (!handoffFreezeGate.matchesCurrentState(workspace, snapshot)) {
				return GateResult.fail(
						gateName,
						"tracked repository state changed since the frozen handoff snapshot was taken - a gate mutated source it should only have read");
			}
			return GateResult.pass(gateName);
		} catch (RuntimeException e) {
			return GateResult.error(gateName, "failed to recompute the repository snapshot: " + e.getMessage());
		}
	}

	private List<String> scriptIntegrityIssues(JsonNode workspacePackageJson) {
		JsonNode scaffoldScripts = scaffoldPackageJson().path("scripts");
		JsonNode workspaceScripts = workspacePackageJson.path("scripts");
		List<String> issues = new ArrayList<>();
		for (String scriptName : REQUIRED_SCRIPTS) {
			String expected = scaffoldScripts.path(scriptName).asString(null);
			String actual = workspaceScripts.path(scriptName).asString(null);
			if (!Objects.equals(expected, actual)) {
				issues.add("required script '" + scriptName + "' was changed from '" + expected + "' to '" + actual + "'");
			}
		}
		return issues;
	}

	private Map<String, String> allDependencies(JsonNode packageJson) {
		Map<String, String> dependencies = new LinkedHashMap<>();
		JsonNode direct = packageJson.path("dependencies");
		direct.propertyNames().forEach(name -> dependencies.put(name, direct.path(name).asString()));
		JsonNode dev = packageJson.path("devDependencies");
		dev.propertyNames().forEach(name -> dependencies.put(name, dev.path(name).asString()));
		return dependencies;
	}

	private JsonNode scaffoldPackageJson() {
		Resource resource = resourceLoader.getResource(SCAFFOLD_PACKAGE_JSON);
		try {
			return objectMapper.readTree(resource.getContentAsString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + SCAFFOLD_PACKAGE_JSON, e);
		}
	}

	private JsonNode readJson(Path path) {
		try {
			return objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + path, e);
		}
	}
}
