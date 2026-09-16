package ai.architech.backend.core.qa.tooling;

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
 * Resolves a {@link QaToolCapabilityProfile} by its {@code ref} (e.g. {@code
 * "website-qa-tools@1.0.0"}, the same string {@code QaExecution.toolCapabilityProfileRef} and
 * {@code QAExecutionPreflightValidator.EXPECTED_TOOL_CAPABILITY_PROFILE_REF} already carry) from
 * any {@code tool-capability-profile.v1.yaml} found under {@code
 * project-types/**}{@code /website-qa-agent/profiles/} on the classpath (AIW-171).
 *
 * <p>Scoped to {@code website-qa-agent} specifically - unlike {@link
 * ai.architech.backend.core.agent.AgentDefinitionLoader}, which is deliberately generic because
 * every agent's own {@code agent.yaml} shares one schema, a QA tool capability profile and a
 * Developer tool capability profile do not share a schema at all (QA's is entirely read-only;
 * Developer's grants filesystem write, project execution and bounded git operations) - a
 * QA-specific loader over a QA-specific path is the honest reflection of that, not an
 * inconsistency with the generic-loader idiom.
 *
 * <p>Resolution is fail-fast, and load-time validation actually enforces AIW-171's own safety
 * contract rather than merely documenting it: every {@link QaToolCapabilityProfile.Security}
 * field must be {@code false}, {@code sourceInspection.readOnly} must be {@code true}, and
 * {@code network.rawOutbound} / {@code safeIntegrationTest.realSideEffects} must be {@code false}
 * - a profile that violates any of these is rejected at resolution time, not silently trusted.
 */
@Component
public class QaToolCapabilityProfileLoader {

	private static final String PROFILE_PATTERN = "classpath*:project-types/**/website-qa-agent/profiles/tool-capability-profile.v1.yaml";

	private final ResourcePatternResolver resourceResolver;
	private final Yaml yaml = new Yaml();

	QaToolCapabilityProfileLoader(ResourcePatternResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public QaToolCapabilityProfile resolve(String ref) {
		for (Resource resource : findProfileResources()) {
			QaToolCapabilityProfile profile = parse(resource);
			if (profile.ref().equals(ref)) {
				return profile;
			}
		}
		throw new QaToolCapabilityProfileNotFoundException(ref);
	}

	/**
	 * The read-only, structural half of AIW-171's own safety contract - decoupled from YAML
	 * parsing and {@link Resource} entirely so it can be exercised directly against a
	 * hand-constructed {@link QaToolCapabilityProfile} (AIW-171's own "representative
	 * security/isolation tests" requirement) without needing a tampered classpath fixture.
	 */
	static List<String> safetyContractViolations(QaToolCapabilityProfile profile) {
		QaToolCapabilityProfile.Security security = profile.security();
		List<String> violations = new ArrayList<>();
		if (security.unrestrictedShell()) {
			violations.add("unrestrictedShell");
		}
		if (security.arbitraryPackageInstallation()) {
			violations.add("arbitraryPackageInstallation");
		}
		if (security.hostFilesystem()) {
			violations.add("hostFilesystem");
		}
		if (security.dockerSocket()) {
			violations.add("dockerSocket");
		}
		if (security.cloudCredentials()) {
			violations.add("cloudCredentials");
		}
		if (security.deploymentCredentials()) {
			violations.add("deploymentCredentials");
		}
		if (security.unrestrictedOutboundNetworking()) {
			violations.add("unrestrictedOutboundNetworking");
		}
		if (!profile.sourceInspection().readOnly()) {
			violations.add("sourceInspection.readOnly must be true");
		}
		if (profile.network().rawOutbound()) {
			violations.add("network.rawOutbound must be false");
		}
		if (profile.safeIntegrationTest().realSideEffects()) {
			violations.add("safeIntegrationTest.realSideEffects must be false");
		}
		return violations;
	}

	private Resource[] findProfileResources() {
		try {
			return resourceResolver.getResources(PROFILE_PATTERN);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to scan for QA tool capability profiles", e);
		}
	}

	@SuppressWarnings("unchecked")
	private QaToolCapabilityProfile parse(Resource resource) {
		Map<String, Object> raw;
		try (InputStream in = resource.getInputStream()) {
			raw = yaml.load(in);
		} catch (IOException e) {
			throw new InvalidQaToolCapabilityProfileException(resource, "Failed to read QA tool capability profile", e);
		}

		try {
			QaToolCapabilityProfile profile = toProfile(raw, resource);
			validateSafetyContract(profile, resource);
			return profile;
		} catch (RuntimeException e) {
			if (e instanceof InvalidQaToolCapabilityProfileException) {
				throw e;
			}
			throw new InvalidQaToolCapabilityProfileException(resource, "Malformed QA tool capability profile", e);
		}
	}

	@SuppressWarnings("unchecked")
	private QaToolCapabilityProfile toProfile(Map<String, Object> raw, Resource resource) {
		List<QaToolCapability> capabilities = ((List<String>) requireField(raw, "capabilities"))
				.stream()
				.map(QaToolCapability::valueOf)
				.toList();

		Map<String, Object> browserRaw = requireMap(raw, "browser");
		Map<String, Object> sourceInspectionRaw = requireMap(raw, "sourceInspection");
		Map<String, Object> networkRaw = requireMap(raw, "network");
		Map<String, Object> safeIntegrationTestRaw = requireMap(raw, "safeIntegrationTest");
		Map<String, Object> securityRaw = requireMap(raw, "security");

		return new QaToolCapabilityProfile(
				(String) requireField(raw, "ref"),
				capabilities,
				new QaToolCapabilityProfile.Browser((Boolean) requireField(browserRaw, "candidateSurfaceOnly")),
				new QaToolCapabilityProfile.SourceInspection(
						(List<String>) requireField(sourceInspectionRaw, "allowed"),
						(Boolean) requireField(sourceInspectionRaw, "readOnly")),
				new QaToolCapabilityProfile.Network(
						(Boolean) requireField(networkRaw, "rawOutbound"),
						(Boolean) requireField(networkRaw, "allowedOnlyThroughAuthorizedCapabilities"),
						(List<String>) requireField(networkRaw, "allowlist")),
				new QaToolCapabilityProfile.SafeIntegrationTest(
						(Boolean) requireField(safeIntegrationTestRaw, "realSideEffects"),
						(Boolean) requireField(safeIntegrationTestRaw, "credentialEncapsulation")),
				new QaToolCapabilityProfile.Security(
						(Boolean) requireField(securityRaw, "unrestrictedShell"),
						(Boolean) requireField(securityRaw, "arbitraryPackageInstallation"),
						(Boolean) requireField(securityRaw, "hostFilesystem"),
						(Boolean) requireField(securityRaw, "dockerSocket"),
						(Boolean) requireField(securityRaw, "cloudCredentials"),
						(Boolean) requireField(securityRaw, "deploymentCredentials"),
						(Boolean) requireField(securityRaw, "unrestrictedOutboundNetworking")));
	}

	private void validateSafetyContract(QaToolCapabilityProfile profile, Resource resource) {
		List<String> violations = safetyContractViolations(profile);
		if (!violations.isEmpty()) {
			throw new InvalidQaToolCapabilityProfileException(resource,
					"QA tool capability profile '" + profile.ref() + "' violates its required safety contract: " + violations);
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
