package ai.architech.backend.core.verification;

import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Machine-enforced protection against weakening mandatory verification surfaces merely to make
 * checks pass (AIW-156) - the layer above AIW-143 gate 1's own script-identity check (which
 * already rejects a hollowed-out {@code typecheck}/{@code lint}/{@code test}/{@code build}
 * script string). This class protects the surfaces a Developer could weaken <em>without</em>
 * touching those script strings at all:
 *
 * <ul>
 *   <li>TypeScript strictness - a fixed allowlist of {@code tsconfig.app.json} compiler flags
 *       (exactly the ones AIW-138's frozen scaffold turns on) can never be flipped off, while
 *       every other compiler option (paths, includes, new libs) remains freely customizable -
 *       this is what keeps "legitimate config modification remains possible" true;
 *   <li>lint rule strictness - every rule the frozen {@code .oxlintrc.json} baseline declares
 *       must remain at least as severe ({@code error} &ge; {@code warn} &ge; {@code off}) in the
 *       workspace's own config, so a blanket downgrade of a required rule is rejected while new,
 *       additional rules the Developer adds are untouched;
 *   <li>baseline test tooling - the workspace must retain at least as many {@code *.test.ts(x)}
 *       files under {@code src} as the frozen scaffold shipped, catching wholesale deletion of
 *       test infrastructure.
 * </ul>
 *
 * <p><strong>Deliberately out of scope:</strong> whether a still-present test is semantically
 * <em>meaningful</em> (rather than a gutted no-op replacement) is a judgment about code content
 * this deterministic checker cannot make - AIW-143's own real test execution (gate 5) already
 * requires whatever tests do exist to actually pass, which is as far as a Runner can verify
 * without understanding what a test is supposed to prove.
 */
@Component
public class VerificationIntegrityGuard {

	private static final String SCAFFOLD_TSCONFIG_APP =
			"classpath:project-types/website/agents/developer-agent/scaffold/tsconfig.app.json";
	private static final String SCAFFOLD_OXLINTRC =
			"classpath:project-types/website/agents/developer-agent/scaffold/.oxlintrc.json";
	private static final List<String> PROTECTED_TYPESCRIPT_STRICTNESS_FLAGS =
			List.of("strict", "noUnusedLocals", "noUnusedParameters", "noFallthroughCasesInSwitch");
	// AIW-138's frozen scaffold ships exactly one test file (src/App.test.tsx) - a fixed constant
	// tied to that known baseline, not a second classpath directory walk of the scaffold itself.
	private static final long SCAFFOLD_BASELINE_TEST_FILE_COUNT = 1;

	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper = new ObjectMapper();

	VerificationIntegrityGuard(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	/** Empty when the workspace's protected verification surfaces still meet the frozen baseline. */
	public List<String> findIntegrityViolations(Workspace workspace) {
		List<String> issues = new ArrayList<>();
		issues.addAll(typescriptStrictnessViolations(workspace));
		issues.addAll(lintRuleStrictnessViolations(workspace));
		issues.addAll(testFileCountViolations(workspace));
		return issues;
	}

	private List<String> typescriptStrictnessViolations(Workspace workspace) {
		JsonNode workspaceTsconfig;
		try {
			workspaceTsconfig = readWorkspaceJson(workspace, "tsconfig.app.json");
		} catch (UncheckedIOException e) {
			return List.of("tsconfig.app.json is missing or unreadable: " + e.getMessage());
		}

		JsonNode scaffoldOptions = readClasspathJson(SCAFFOLD_TSCONFIG_APP).path("compilerOptions");
		JsonNode workspaceOptions = workspaceTsconfig.path("compilerOptions");

		List<String> issues = new ArrayList<>();
		for (String flag : PROTECTED_TYPESCRIPT_STRICTNESS_FLAGS) {
			boolean expected = scaffoldOptions.path(flag).asBoolean(false);
			boolean actual = workspaceOptions.path(flag).asBoolean(false);
			if (expected && !actual) {
				issues.add("tsconfig.app.json compilerOptions." + flag + " was weakened from 'true' to '" + actual + "'");
			}
		}
		return issues;
	}

	private List<String> lintRuleStrictnessViolations(Workspace workspace) {
		JsonNode workspaceOxlintrc;
		try {
			workspaceOxlintrc = readWorkspaceJson(workspace, ".oxlintrc.json");
		} catch (UncheckedIOException e) {
			return List.of(".oxlintrc.json is missing or unreadable: " + e.getMessage());
		}

		JsonNode scaffoldRules = readClasspathJson(SCAFFOLD_OXLINTRC).path("rules");
		JsonNode workspaceRules = workspaceOxlintrc.path("rules");

		List<String> issues = new ArrayList<>();
		scaffoldRules.propertyNames().forEach(ruleName -> {
			int expectedSeverity = severityRank(scaffoldRules.path(ruleName));
			int actualSeverity = severityRank(workspaceRules.path(ruleName));
			if (actualSeverity < expectedSeverity) {
				issues.add("oxlint rule '" + ruleName + "' was weakened from severity " + expectedSeverity + " to " + actualSeverity);
			}
		});
		return issues;
	}

	/** A rule's value is either a bare severity string or a {@code [severity, ...options]} array - {@code error} > {@code warn} > {@code off}/absent. */
	private int severityRank(JsonNode ruleValue) {
		JsonNode severityNode = ruleValue.isArray() ? ruleValue.get(0) : ruleValue;
		if (severityNode == null || severityNode.isMissingNode()) {
			return 0;
		}
		return switch (severityNode.asString("off")) {
			case "error" -> 2;
			case "warn" -> 1;
			default -> 0;
		};
	}

	private List<String> testFileCountViolations(Workspace workspace) {
		Path srcRoot = workspace.root().resolve("src");
		long workspaceTestFileCount;
		try (Stream<Path> files = Files.walk(srcRoot)) {
			workspaceTestFileCount = files.filter(this::isTestFile).count();
		} catch (IOException e) {
			return List.of("failed to enumerate test files under src: " + e.getMessage());
		}
		if (workspaceTestFileCount < SCAFFOLD_BASELINE_TEST_FILE_COUNT) {
			return List.of("test file count dropped from " + SCAFFOLD_BASELINE_TEST_FILE_COUNT + " to " + workspaceTestFileCount
					+ " - baseline test tooling appears to have been deleted");
		}
		return List.of();
	}

	private boolean isTestFile(Path path) {
		String name = path.getFileName().toString();
		return Files.isRegularFile(path) && (name.endsWith(".test.ts") || name.endsWith(".test.tsx"));
	}

	private JsonNode readWorkspaceJson(Workspace workspace, String relativePath) {
		try {
			return objectMapper.readTree(Files.readString(workspace.root().resolve(relativePath), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + relativePath, e);
		}
	}

	private JsonNode readClasspathJson(String classpathLocation) {
		Resource resource = resourceLoader.getResource(classpathLocation);
		try {
			return objectMapper.readTree(resource.getContentAsString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + classpathLocation, e);
		}
	}
}
