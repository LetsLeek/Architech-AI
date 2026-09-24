package ai.architech.backend.core.dependency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces AIW-140's Website Developer V1 dependency-admission boundary
 * ({@code dependency-policy.v1.yaml}): every requested package/version-spec and every
 * manifest-declared lifecycle script is classified and given a policy outcome - detected and
 * reported only, exactly like {@link ai.architech.backend.core.validation.ArtifactSchemaValidator}'s
 * own contract, never silently rewritten or stripped.
 *
 * <p>"Platform-authorized registry" (the policy's only {@code allowedSources} entry) resolves
 * concretely here to: no prohibited source syntax (git/URL/tarball/local-file - the structural
 * signals a manifest+lockfile pair can actually show; policy-level allowlisting of an
 * <em>alternate</em> registry is an {@code .npmrc}-level concern enforced by the sandbox itself,
 * AIW-139/153, not detectable from a manifest in isolation) plus one of two admission paths:
 * already part of the Development Base's own pre-vetted dependency set (AIW-138's scaffold
 * {@code package.json}, read directly here so the two can never silently drift apart), or a
 * plain registry-sourced, pinned version spec that is policy-eligible but not yet platform-
 * approved.
 */
@Component
public class DependencyPolicyClassifier {

	private static final String SCAFFOLD_PACKAGE_JSON =
			"classpath:project-types/website/agents/developer-agent/scaffold/package.json";

	/**
	 * npm's own lifecycle hook names (https://docs.npmjs.com/cli/v11/using-npm/scripts) - the
	 * ones that run automatically during install, which is exactly what
	 * {@code lifecycleScripts.default: disabled} exists to prevent. Ordinary project scripts
	 * (build/test/lint/dev/...) are never lifecycle scripts and are never flagged here.
	 */
	private static final Set<String> LIFECYCLE_SCRIPT_NAMES =
			Set.of("preinstall", "install", "postinstall", "prepare", "prepublish", "preprepare", "postprepare");

	private final Set<String> platformApprovedPackageNames;

	DependencyPolicyClassifier(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
		this.platformApprovedPackageNames = loadPlatformApprovedPackageNames(resourceLoader, objectMapper);
	}

	public DependencyPolicyFinding classifyDependency(String packageName, String versionSpec) {
		String prohibitedSourceReason = prohibitedSourceReason(versionSpec);
		if (prohibitedSourceReason != null) {
			return new DependencyPolicyFinding(
					packageName,
					versionSpec,
					DependencyClassification.PROHIBITED,
					DependencyPolicyOutcome.BLOCK,
					prohibitedSourceReason);
		}

		if (platformApprovedPackageNames.contains(packageName)) {
			return new DependencyPolicyFinding(
					packageName,
					versionSpec,
					DependencyClassification.PLATFORM_APPROVED,
					DependencyPolicyOutcome.PASS,
					"already part of the platform-approved Development Base dependency set");
		}

		if (isUnpinnedRange(versionSpec)) {
			return new DependencyPolicyFinding(
					packageName,
					versionSpec,
					DependencyClassification.POLICY_ELIGIBLE,
					DependencyPolicyOutcome.WARN,
					"version spec '" + versionSpec
							+ "' is an unpinned/wildcard range, which weakens reproducible installs - review recommended");
		}

		return new DependencyPolicyFinding(
				packageName,
				versionSpec,
				DependencyClassification.POLICY_ELIGIBLE,
				DependencyPolicyOutcome.PASS,
				"registry-sourced, pinned dependency - structurally policy-compliant though not yet platform-approved");
	}

	/**
	 * Only npm's own lifecycle hook names are ever flagged - see {@link #LIFECYCLE_SCRIPT_NAMES}.
	 * V1 admits no exception ({@code exceptionRequiresPlatformPolicy: true} with no exception
	 * mechanism built yet), so every match is an unconditional BLOCK.
	 */
	public List<DependencyPolicyFinding> classifyLifecycleScripts(Map<String, String> packageJsonScripts) {
		return packageJsonScripts.keySet().stream()
				.filter(LIFECYCLE_SCRIPT_NAMES::contains)
				.map(scriptName -> new DependencyPolicyFinding(
						"<manifest lifecycle script>",
						scriptName,
						DependencyClassification.PROHIBITED,
						DependencyPolicyOutcome.BLOCK,
						"lifecycle script '" + scriptName
								+ "' runs automatically during install and is disabled by default; V1 admits no exception"))
				.toList();
	}

	private String prohibitedSourceReason(String versionSpec) {
		String trimmed = versionSpec.trim();
		if (trimmed.startsWith("git+") || trimmed.startsWith("git:") || trimmed.startsWith("github:")) {
			return "version spec '" + versionSpec + "' resolves via git, a prohibited dependency source";
		}
		if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
			return "version spec '" + versionSpec + "' is an arbitrary/tarball URL, a prohibited dependency source";
		}
		if (trimmed.startsWith("file:")) {
			return "version spec '" + versionSpec + "' is a local file reference (manual vendoring), a prohibited dependency source";
		}
		return null;
	}

	private boolean isUnpinnedRange(String versionSpec) {
		String trimmed = versionSpec.trim();
		return trimmed.isEmpty() || trimmed.equals("*") || trimmed.equalsIgnoreCase("latest");
	}

	private Set<String> loadPlatformApprovedPackageNames(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
		Resource resource = resourceLoader.getResource(SCAFFOLD_PACKAGE_JSON);
		JsonNode packageJson;
		try {
			packageJson = objectMapper.readTree(resource.getContentAsString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + SCAFFOLD_PACKAGE_JSON, e);
		}

		Set<String> names = new HashSet<>();
		packageJson.path("dependencies").propertyNames().forEach(names::add);
		packageJson.path("devDependencies").propertyNames().forEach(names::add);
		return names;
	}
}
