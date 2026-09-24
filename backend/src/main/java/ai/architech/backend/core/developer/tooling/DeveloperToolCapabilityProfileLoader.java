package ai.architech.backend.core.developer.tooling;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Resolves a {@link DeveloperToolCapabilityProfile} by its {@code id} (e.g. {@code
 * "website-developer-tools-v1"}, the exact string {@code DeveloperExecutionInputAssembler}
 * already embeds into every assembled {@code developer-execution-input.v1} payload's {@code
 * technicalContext.toolCapabilityProfileRef}) from any {@code tool-capability-profile.v1.yaml}
 * found under {@code project-types/**}{@code /developer-agent/profiles/} on the classpath
 * (AIW-184).
 *
 * <p>Matches by {@code id} rather than a {@code ref} field, unlike {@code
 * QaToolCapabilityProfileLoader} - Developer's own YAML carries separate {@code id}/{@code
 * version} fields, never a concatenated {@code id@version} ref string. Scoped to {@code
 * developer-agent} specifically for the same reason the QA loader is scoped to
 * {@code website-qa-agent}: the two YAML shapes do not share a schema at all (QA's is entirely
 * read-only; Developer's grants filesystem write, project execution and bounded git operations).
 *
 * <p>Resolution is fail-fast, and load-time validation actually enforces the profile's own
 * required safety posture rather than merely trusting it: {@link
 * DeveloperToolCapabilityProfile.Security#nonRoot()} must be {@code true} and every other {@code
 * Security} field must be {@code false}; {@code projectExecution.unrestrictedShell} and {@code
 * network.rawOutbound} must be {@code false}; and none of git's own well-known dangerous
 * subcommands (branch mutation, checkout/switch, merge, rebase, agent-authored commits, push,
 * force-push, remote credential access) may ever appear in {@code git.allowed} - checked directly
 * against the permissive field itself, not merely inferred from the separate {@code prohibited}
 * list a YAML edit could otherwise drift out of sync with.
 */
@Component
public class DeveloperToolCapabilityProfileLoader {

	private static final String PROFILE_PATTERN =
			"classpath*:project-types/**/developer-agent/profiles/tool-capability-profile.v1.yaml";

	private static final List<String> DANGEROUS_GIT_SUBCOMMANDS = List.of(
			"branch", "checkout-or-switch", "merge", "rebase", "commit-by-agent", "push", "force-push", "remote-credential-access");

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	DeveloperToolCapabilityProfileLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public DeveloperToolCapabilityProfile resolve(String id) {
		for (Resource resource : findProfileResources()) {
			DeveloperToolCapabilityProfile profile = parse(resource);
			if (profile.id().equals(id)) {
				return profile;
			}
		}
		throw new DeveloperToolCapabilityProfileNotFoundException(id);
	}

	/**
	 * The read-only, structural half of this profile's own safety contract - decoupled from YAML
	 * parsing and {@link Resource} entirely so it can be exercised directly against a
	 * hand-constructed {@link DeveloperToolCapabilityProfile} without needing a tampered classpath
	 * fixture.
	 */
	static List<String> safetyContractViolations(DeveloperToolCapabilityProfile profile) {
		List<String> violations = new ArrayList<>();
		DeveloperToolCapabilityProfile.Security security = profile.security();
		if (!security.nonRoot()) {
			violations.add("security.nonRoot must be true");
		}
		if (security.dockerSocket()) {
			violations.add("security.dockerSocket must be false");
		}
		if (security.cloudCredentials()) {
			violations.add("security.cloudCredentials must be false");
		}
		if (security.platformSecrets()) {
			violations.add("security.platformSecrets must be false");
		}
		if (profile.projectExecution().unrestrictedShell()) {
			violations.add("projectExecution.unrestrictedShell must be false");
		}
		if (profile.network().rawOutbound()) {
			violations.add("network.rawOutbound must be false");
		}
		for (String dangerous : DANGEROUS_GIT_SUBCOMMANDS) {
			if (profile.git().allowed().contains(dangerous)) {
				violations.add("git.allowed must never contain '" + dangerous + "'");
			}
		}
		return violations;
	}

	private Resource[] findProfileResources() {
		try {
			return resourceResolver.getResources(PROFILE_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for Developer tool capability profiles", e);
		}
	}

	@SuppressWarnings("unchecked")
	private DeveloperToolCapabilityProfile parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidDeveloperToolCapabilityProfileException(resource, "Failed to read Developer tool capability profile", e);
		}

		try {
			DeveloperToolCapabilityProfile profile = toProfile(raw);
			validateSafetyContract(profile, resource);
			return profile;
		} catch (RuntimeException e) {
			if (e instanceof InvalidDeveloperToolCapabilityProfileException) {
				throw e;
			}
			throw new InvalidDeveloperToolCapabilityProfileException(resource, "Malformed Developer tool capability profile", e);
		}
	}

	@SuppressWarnings("unchecked")
	private DeveloperToolCapabilityProfile toProfile(Map<String, Object> raw) {
		Map<String, Object> filesystemRaw = requireMap(raw, "filesystem");
		Map<String, Object> projectExecutionRaw = requireMap(raw, "projectExecution");
		Map<String, Object> dependenciesRaw = requireMap(raw, "dependencies");
		Map<String, Object> gitRaw = requireMap(raw, "git");
		Map<String, Object> browserRaw = requireMap(raw, "browser");
		Map<String, Object> networkRaw = requireMap(raw, "network");
		Map<String, Object> securityRaw = requireMap(raw, "security");

		return new DeveloperToolCapabilityProfile(
				(String) requireField(raw, "id"),
				new DeveloperToolCapabilityProfile.Filesystem(
						(List<String>) requireField(filesystemRaw, "allowed"),
						(Boolean) requireField(filesystemRaw, "workspaceOnly"),
						(List<String>) requireField(filesystemRaw, "protected")),
				new DeveloperToolCapabilityProfile.ProjectExecution(
						(List<String>) requireField(projectExecutionRaw, "approvedTasks"),
						(Boolean) requireField(projectExecutionRaw, "unrestrictedShell")),
				(Boolean) requireField(dependenciesRaw, "managedCapability"),
				new DeveloperToolCapabilityProfile.Git(
						(List<String>) requireField(gitRaw, "allowed"), (List<String>) requireField(gitRaw, "prohibited")),
				(Boolean) requireField(browserRaw, "localRuntimeOnly"),
				new DeveloperToolCapabilityProfile.Network(
						(Boolean) requireField(networkRaw, "rawOutbound"),
						(Boolean) requireField(networkRaw, "allowedOnlyThroughAuthorizedCapabilities")),
				new DeveloperToolCapabilityProfile.Security(
						(Boolean) requireField(securityRaw, "nonRoot"),
						(Boolean) requireField(securityRaw, "dockerSocket"),
						(Boolean) requireField(securityRaw, "cloudCredentials"),
						(Boolean) requireField(securityRaw, "platformSecrets")));
	}

	private void validateSafetyContract(DeveloperToolCapabilityProfile profile, Resource resource) {
		List<String> violations = safetyContractViolations(profile);
		if (!violations.isEmpty()) {
			throw new InvalidDeveloperToolCapabilityProfileException(
					resource, "Developer tool capability profile '" + profile.id() + "' violates its required safety contract: " + violations);
		}
	}

	private static Map<String, Object> requireMap(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (!(value instanceof Map)) {
			throw new IllegalStateException("Missing required object field: " + field);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) value;
		return map;
	}

	private static Object requireField(Map<String, Object> raw, String field) {
		Object value = raw.get(field);
		if (value == null) {
			throw new IllegalStateException("Missing required field: " + field);
		}
		return value;
	}
}
