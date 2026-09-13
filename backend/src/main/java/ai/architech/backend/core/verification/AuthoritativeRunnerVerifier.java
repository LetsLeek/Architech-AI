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
 * handoff state (AIW-143). Runs a fixed sequence of mandatory gates and stops at the first
 * non-{@code PASS} one (a technical-source or infrastructure failure cascading into every later
 * gate would just repeat the same underlying cause with no new information) - see {@link
 * RunnerVerificationResult} for how the overall outcome is computed from whichever gates
 * actually ran.
 *
 * <p><strong>Scope of this V1 slice:</strong> this class implements the eight gates that are
 * fully deterministic and buildable against infrastructure that already exists in this codebase
 * (repository/dependency integrity, clean policy-compliant install, typecheck, lint, test,
 * build, secret/credential scan, final source-state integrity - AIW-143's own gates 1-6, 13 and
 * 14). Gates 7-12 (local runtime startup, canonical route smoke, primary navigation smoke,
 * browser/runtime integrity, and the two representative responsive-sanity gates) require a real
 * browser-automation harness driving a locally-started dev server against specific viewport
 * projects - infrastructure AIW-157 (browser runtime/network policy + Playwright viewport
 * projects) has not built yet. Deferring those six gates here, explicitly, rather than faking
 * or skipping them silently, was a deliberate scoping decision for this ticket (see the M3
 * sequencing plan): {@link #verify} never claims a {@code PASS} outcome for a gate it did not
 * actually run - it simply does not run gates 7-12 at all yet.
 */
@Component
public class AuthoritativeRunnerVerifier {

	private static final String SCAFFOLD_PACKAGE_JSON =
			"classpath:project-types/website/agents/developer-agent/scaffold/package.json";
	private static final List<String> REQUIRED_SCRIPTS = List.of("typecheck", "lint", "test", "build");

	private final DependencyPolicyClassifier dependencyPolicyClassifier;
	private final LockfileConsistencyChecker lockfileConsistencyChecker;
	private final SecretScanGate secretScanGate;
	private final HandoffFreezeGate handoffFreezeGate;
	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper = new ObjectMapper();

	AuthoritativeRunnerVerifier(
			DependencyPolicyClassifier dependencyPolicyClassifier,
			LockfileConsistencyChecker lockfileConsistencyChecker,
			SecretScanGate secretScanGate,
			HandoffFreezeGate handoffFreezeGate,
			ResourceLoader resourceLoader) {
		this.dependencyPolicyClassifier = dependencyPolicyClassifier;
		this.lockfileConsistencyChecker = lockfileConsistencyChecker;
		this.secretScanGate = secretScanGate;
		this.handoffFreezeGate = handoffFreezeGate;
		this.resourceLoader = resourceLoader;
	}

	public RunnerVerificationResult verify(Workspace workspace, FrozenHandoffSnapshot snapshot) {
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
		if (!runGate(gates, () -> secretCredentialScanGate(workspace))) {
			return new RunnerVerificationResult(gates);
		}
		runGate(gates, () -> finalSourceStateIntegrityGate(workspace, snapshot));

		return new RunnerVerificationResult(gates);
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
